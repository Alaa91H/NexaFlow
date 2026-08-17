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
                    Boolean::class.javaObjectType -> Boolean::class.javaPrimitiveType ?: type
                    Int::class.javaObjectType -> Int::class.javaPrimitiveType ?: type
                    Long::class.javaObjectType -> Long::class.javaPrimitiveType ?: type
                    Float::class.javaObjectType -> Float::class.javaPrimitiveType ?: type
                    Double::class.javaObjectType -> Double::class.javaPrimitiveType ?: type
                    Byte::class.javaObjectType -> Byte::class.javaPrimitiveType ?: type
                    Short::class.javaObjectType -> Short::class.javaPrimitiveType ?: type
                    Char::class.javaObjectType -> Char::class.javaPrimitiveType ?: type
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
