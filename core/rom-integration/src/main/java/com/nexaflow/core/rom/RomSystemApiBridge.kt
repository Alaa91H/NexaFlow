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
            val method = instance.javaClass.getMethod(methodName, *types)
            method.invoke(instance, *args)
        } catch (_: Throwable) {
            null
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
