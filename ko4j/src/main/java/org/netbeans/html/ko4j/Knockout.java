/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.netbeans.html.ko4j;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.Executor;
import net.java.html.js.JavaScriptBody;
import net.java.html.js.JavaScriptResource;
import net.java.html.json.Model;
import org.netbeans.html.boot.spi.Fn;
import org.netbeans.html.json.spi.FunctionBinding;
import org.netbeans.html.json.spi.PropertyBinding;

/** This is an implementation package - just
 * include its JAR on classpath and use official {@link Context} API
 * to access the functionality.
 * <p>
 * Provides binding between {@link Model models} and knockout.js running
 * inside a JavaFX WebView.
 *
 * @author Jaroslav Tulach
 */
@JavaScriptResource("knockout-3.5.0.js")
final class Knockout  {

    @JavaScriptBody(args = {"object", "property"}, body =
        "var ret;\n" +
        "if (property === null) ret = object;\n" +
        "else if (object === null) ret = null;\n" +
        "else ret = object[property];\n" +
        "if (typeof ret !== 'undefined' && ret !== null) {\n" +
        "  if (typeof ko !== 'undefined' && ko['utils'] && ko['utils']['unwrapObservable']) {\n" +
        "    return ko['utils']['unwrapObservable'](ret);\n" +
        "  }\n" +
        "  return ret;\n" +
        "}\n" +
        "return null;\n"
    )
    static Object getProperty(Object object, String property) {
        return null;
    }

    private PropertyBinding[] props;
    private FunctionBinding[] funcs;
    private Object objs;
    private final Object copyFrom;
    private final Object strong;

    public Knockout(Object model, Object copyFrom, PropertyBinding[] props, FunctionBinding[] funcs) {
        this.strong = model;
        this.props = new PropertyBinding[props.length];
        for (int i = 0; i < props.length; i++) {
            this.props[i] = props[i].weak();
        }
        this.funcs = new FunctionBinding[funcs.length];
        for (int i = 0; i < funcs.length; i++) {
            this.funcs[i] = funcs[i].weak();
        }
        this.copyFrom = copyFrom;
    }

    final Object js() {
        final Fn.Presenter c = Fn.activePresenter();
        Object js = MapObjs.get(objs, c);
        if (js == null) {
            js = initObjs(c, copyFrom);
            objs = MapObjs.put(objs, c, js);
        }
        return js;
    }

    private Object initObjs(Fn.Presenter p, Object copyFrom) {
        String[] propNames = new String[props.length];
        Number[] propInfo = new Number[props.length];
        Object[] propValues = new Object[props.length];
        for (int i = 0; i < propNames.length; i++) {
            propNames[i] = props[i].getPropertyName();
            int info
                    = (props[i].isReadOnly() ? 1 : 0)
                    + (props[i].isConstant() ? 2 : 0);
            propInfo[i] = info;
            Object value = props[i].getValue();
            if (value instanceof Enum) {
                value = value.toString();
            }
            propValues[i] = value;
        }
        String[] funcNames = new String[funcs.length];
        for (int i = 0; i < funcNames.length; i++) {
            funcNames[i] = funcs[i].getFunctionName();
        }
        Object ret = CacheObjs.find(p).getJSObject();
        wrapModel(this,ret, copyFrom, propNames, propInfo, propValues, funcNames);
        return ret;
    }

    static void cleanUp() {
        for (;;) {
            Knockout ko = null;
            if (ko == null) {
                return;
            }
            Object[] both = MapObjs.remove(ko.objs, Fn.activePresenter());
            Object js = both[0];
            ko.objs = both[1];
            clean(js);
            ko.props = null;
            ko.funcs = null;
        }
    }

    final void hold() {
    }

    final Object get() {
        return strong;
    }

    final Object getValue(int index) {
        return props[index].getValue();
    }

    final void setValue(int index, Object v) {
        if (v instanceof Knockout) {
            v = ((Knockout)v).get();
        }
        props[index].setValue(v);
    }

    final void call(int index, Object data, Object ev) {
        funcs[index].call(data, ev);
    }

    private static Fn.Presenter getPresenter(Object obj) {
        if (obj instanceof Fn.Presenter) {
            return (Fn.Presenter) obj;
        } else {
            if (obj == null) {
                return null;
            } else {
                return ((Fn.Ref) obj).presenter();
            }
        }
    }

    final void valueHasMutated(final String propertyName, Object oldValue, Object newValue) {
        Object[] all = MapObjs.toArray(objs);
        for (int i = 0; i < all.length; i += 2) {
            Fn.Presenter p = getPresenter(all[i]);
            final Object o = all[i + 1];
            if (p != Fn.activePresenter()) {
                if (p instanceof Executor) {
                    ((Executor) p).execute(new Runnable() {
                        @Override
                        public void run() {
                            valueHasMutated(o, propertyName, null, null);
                        }
                    });
                } else {
                    Closeable c = Fn.activate(p);
                    try {
                        valueHasMutated(o, propertyName, null, null);
                    } finally {
                        try {
                            c.close();
                        } catch (IOException ex) {
                        }
                    }
                }
            }
        }
        valueHasMutated(js(), propertyName, oldValue, newValue);
    }

    @JavaScriptBody(args = { "model", "prop", "oldValue", "newValue" },
        wait4js = false,
        body =
          "if (model) {\n"
        + "  var koProp = model[prop];\n"
        + "  if (koProp) {\n"
        + "    var koFire = koProp['valueHasMutated'];\n"
        + "    if (koFire) {\n"
        + "      if (oldValue !== null || newValue !== null) {\n"
        + "        koFire(newValue);\n"
        + "      } else {\n"
        + "        koFire();\n"
        + "      }\n"
        + "    }\n"
        + "  }\n"
        + "}\n"
    )
    private native static void valueHasMutated(
        Object model, String prop, Object oldValue, Object newValue
    );

    final Object applyBindings(String id) {
        return applyBindings(id, js());
    }

    @JavaScriptBody(args = { "id", "bindings" }, body =
        "var d = window['document'];\n" +
        "var e = id ? d['getElementById'](id) : d['body'];\n" +
        "ko['cleanNode'](e);\n" +
        "ko['applyBindings'](bindings, e);\n" +
        "return bindings['ko4j'];\n"
    )
    private native static Object applyBindings(String id, Object bindings);

    @JavaScriptBody(args = { "cnt" }, body =
        "var arr = new Array(cnt);\n" +
        "for (var i = 0; i < cnt; i++) arr[i] = new Object();\n" +
        "return arr;\n"
    )
    native static Object[] allocJS(int cnt);

    @JavaScriptBody(
        javacall = true,
        keepAlive = false,
        wait4js = false,
        args = { "thiz", "ret", "copyFrom", "propNames", "propInfo", "propValues", "funcNames" },
        body =
          "Object.defineProperty(ret, 'ko4j', { value : thiz });\n"
        + "function normalValue(r) {\n"
        + "  if (r) try { var br = r.valueOf(); } catch (err) {}\n"
        + "  return br === undefined ? r: br;\n"
        + "}\n"
        + "function koComputed(index, name, readOnly, value) {\n"
        + "  var orig = copyFrom ? copyFrom[name] : null;\n"
        + "  if (!ko['isObservable'](orig)) {\n"
        + "    orig = null;\n"
        + "    var trigger = ko['observable']()['extend']({'notify':'always'});\n"
        + "  } else {\n"
        + "    var trigger = orig;\n"
        + "  }\n"
        + "  function realGetter() {\n"
        + "    var self = ret['ko4j'];\n"
        + "    try {\n"
        + "      var v = self ? self.@org.netbeans.html.ko4j.Knockout::getValue(I)(index) : null;\n"
        + "      return v;\n"
        + "    } catch (e) {\n"
        + "      alert(\"Cannot call getValue on \" + self + \" prop: \" + name + \" error: \" + e);\n"
        + "    }\n"
        + "  }\n"
        + "  var activeGetter = orig ? orig : function() { return value; };\n"
        + "  var bnd = {\n"
        + "    'read': function() {\n"
        + "      trigger();\n"
        + "      if (orig) {\n"
        + "        var r = orig();\n"
        + "      } else {\n"
        + "        var r = activeGetter();\n"
        + "        activeGetter = realGetter;\n"
        + "      }\n"
        + "      return normalValue(r);;\n"
        + "    },\n"
        + "    'owner': ret\n"
        + "  };\n"
        + "  if (!readOnly) {\n"
        + "    function write(val) {\n"
        + "      if (orig) orig(val);\n"
        + "      var self = ret['ko4j'];\n"
        + "      if (!self) return;\n"
        + "      var model = val ? val['ko4j'] : null;\n"
        + "      self.@org.netbeans.html.ko4j.Knockout::setValue(ILjava/lang/Object;)(index, model ? model : val);\n"
        + "    };\n"
        + "    bnd['write'] = write;\n"
        + "    if (orig) {\n"
        + "      write(orig());\n"
        + "      orig.subscribe(write);\n"
        + "    }\n"
        + "  };\n"
        + "  var cmpt = ko['computed'](bnd);\n"
        + "  cmpt['valueHasMutated'] = function(val) {\n"
        + "    if (arguments.length === 1) activeGetter = function() { return val; };\n"
        + "    trigger(val);\n"
        + "  };\n"
        + "  ret[name] = cmpt;\n"
        + "}\n"
        + "for (var i = 0; i < propNames.length; i++) {\n"
        + "  if ((propInfo[i] & 2) !== 0) {\n"
        + "    ret[propNames[i]] = normalValue(propValues[i]);\n"
        + "  } else {\n"
        + "    koComputed(i, propNames[i], (propInfo[i] & 1) !== 0, propValues[i]);\n"
        + "  }\n"
        + "}\n"
        + "function koExpose(index, name) {\n"
        + "  ret[name] = function(data, ev) {\n"
        + "    var self = ret['ko4j'];\n"
        + "    if (!self) return;\n"
        + "    self.@org.netbeans.html.ko4j.Knockout::call(ILjava/lang/Object;Ljava/lang/Object;)(index, data, ev);\n"
        + "  };\n"
        + "}\n"
        + "for (var i = 0; i < funcNames.length; i++) {\n"
        + "  koExpose(i, funcNames[i]);\n"
        + "}\n"
        )
    private static native void wrapModel(
        Knockout thiz,
        Object ret, Object copyFrom,
        String[] propNames, Number[] propInfo,
        Object propValues,
        String[] funcNames
    );

    @JavaScriptBody(args = { "js" }, wait4js = false, body =
        "delete js['ko4j'];\n" +
        "for (var p in js) {\n" +
        "  delete js[p];\n" +
        "};\n" +
        "\n"
    )
    private static native void clean(Object js);

    @JavaScriptBody(args = { "o" }, body = "return o['ko4j'] ? o['ko4j'] : o;")
    private static native Object toModelImpl(Object wrapper);
    static Object toModel(Object wrapper) {
        Object o = toModelImpl(wrapper);
        if (o instanceof Knockout) {
            return ((Knockout)o).get();
        } else {
            return o;
        }
    }
}
