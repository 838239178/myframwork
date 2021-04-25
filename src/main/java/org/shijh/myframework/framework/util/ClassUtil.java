package org.shijh.myframework.framework.util;

public abstract class ClassUtil {

    private static final Class<?>[] primitiveClasses = new Class[]{int.class, long.class, float.class, double.class,
            char.class, boolean.class, byte.class, short.class};

    public static Class<?> primitiveClass(Class<? extends Number> target) {
        for (Class<?> aClass : primitiveClasses) {
            if (target.getSimpleName().toLowerCase().matches(aClass.getName() + ".*")) {
                return aClass;
            }
        }
        return null;
    }

    public static ClassLoader getDefaultClassLoader() {
        ClassLoader cl = null;

        try {
            cl = Thread.currentThread().getContextClassLoader();
        } catch (Throwable ignore) { }

        if (cl == null) {
            cl = ClassUtil.class.getClassLoader();
            if (cl == null) {
                try {
                    cl = ClassLoader.getSystemClassLoader();
                } catch (Throwable ignore) { }
            }
        }
        return cl;
    }
}
