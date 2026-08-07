package com.nexaflow.core.rom

object RomSystemApiBridge {
    fun invokeStatic(className: String, methodName: String, vararg args: Any?): Any? {
        return try {
            val clazz = Class.forName(className)
            val types = args.map { it?.javaClass ?: Int::class.java }.toTypedArray()
            val method = clazz.getMethod(methodName, *types)
            method.invoke(null, *args)
        } catch (_: Throwable) {
            null
        }
    }

    fun invokeInstance(instance: Any, methodName: String, vararg args: Any?): Any? {
        return try {
            val types = args.map { it?.javaClass ?: Int::class.java }.toTypedArray()
            val method = findMethod(instance.javaClass, methodName, types)
            method.invoke(instance, *args)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Resolves the method, first with the exact argument types and then with
     * the primitive counterparts (e.g. `Boolean` → `boolean`) so hidden
     * system APIs like `NfcManager.setNfcEnabled(boolean)` are found even when
     * the caller passes boxed values.
     */
    private fun findMethod(clazz: Class<*>, methodName: String, types: Array<Class<*>>): java.lang.reflect.Method {
        return try {
            clazz.getMethod(methodName, *types)
        } catch (_: NoSuchMethodException) {
            val primitive = types.map { type ->
                when (type) {
                    java.lang.Boolean::class.java -> java.lang.Boolean.TYPE
                    java.lang.Integer::class.java -> java.lang.Integer.TYPE
                    java.lang.Long::class.java -> java.lang.Long.TYPE
                    java.lang.Float::class.java -> java.lang.Float.TYPE
                    java.lang.Double::class.java -> java.lang.Double.TYPE
                    java.lang.Byte::class.java -> java.lang.Byte.TYPE
                    java.lang.Short::class.java -> java.lang.Short.TYPE
                    java.lang.Character::class.java -> java.lang.Character.TYPE
                    else -> type
                }
            }.toTypedArray()
            clazz.getMethod(methodName, *primitive)
        }
    }

    fun readStaticField(className: String, fieldName: String): Any? {
        return try {
            val clazz = Class.forName(className)
            clazz.getField(fieldName).get(null)
        } catch (_: Throwable) {
            null
        }
    }
}
