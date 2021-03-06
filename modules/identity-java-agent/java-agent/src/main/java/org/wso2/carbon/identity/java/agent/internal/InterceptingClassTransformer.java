/*
 * Copyright (c) 2019, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.identity.java.agent.internal;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.scopedpool.ScopedClassPoolFactoryImpl;
import javassist.scopedpool.ScopedClassPoolRepositoryImpl;
import org.wso2.carbon.identity.java.agent.config.InterceptorConfig;

import java.io.ByteArrayInputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Class transformer which intercepts the method call, used to emit the debug information.
 */
public class InterceptingClassTransformer implements ClassFileTransformer {

    /**
     * We use JUL as this is an java agent which should not depend on any other framework than java.
     */
    private final static Logger log = Logger.getLogger(InterceptingClassTransformer.class.getName());

    private final Map<String, InterceptorConfig> interceptorMap = new HashMap<>();
    private ScopedClassPoolFactoryImpl scopedClassPoolFactory = new ScopedClassPoolFactoryImpl();
    private MethodEntryListener methodEntryListener = new MethodEntryListener();
    private ClassPool rootPool;

    public void init() {

        rootPool = ClassPool.getDefault();
    }

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined,
                            ProtectionDomain protectionDomain, byte[] classfileBuffer)
            throws IllegalClassFormatException {

        byte[] byteCode = classfileBuffer;

        if (shouldIntercept(loader, className)) {
            log.fine("Transforming the class " + className);
            boolean isTransformed = false;
            InterceptorConfig config = getInterceptorConfig(loader, className);
            try {

                ClassPool classPool = scopedClassPoolFactory.create(loader, rootPool,
                        ScopedClassPoolRepositoryImpl.getInstance());
                CtClass ctClass = classPool.makeClass(new ByteArrayInputStream(
                        classfileBuffer));
                CtMethod[] methods = ctClass.getDeclaredMethods();

                for (CtMethod method : methods) {
                    if (config.hasMethodSignature(method.getName(), method.getSignature())) {
                        log.info(
                                "Intercepted method " + className + "." + method.getName() + " " + method.getSignature());
                        isTransformed = true;
                        method.insertBefore(
                                "org.wso2.carbon.identity.java.agent.internal.MethodEntryListener.methodEntered(\"" + method.getName() + "\", \"" + method.getSignature() + "\", $args);");
                    }
                }
                if (isTransformed) {
                    byteCode = ctClass.toBytecode();
                }
                ctClass.detach();
            } catch (Throwable ex) {
                log.log(Level.SEVERE, "Error in transforming the class: " + className, ex);
            }
        }
        return byteCode;

    }

    private boolean shouldIntercept(ClassLoader loader, String className) {

        if (interceptorMap.keySet().contains(className)) {
            return true;
        }
        return false;
    }

    private InterceptorConfig getInterceptorConfig(ClassLoader loader, String className) {

        return interceptorMap.get(className);
    }

    public void addConfig(InterceptorConfig interceptorConfig) {

        interceptorMap.put(interceptorConfig.getClassName(), interceptorConfig);
    }
}
