package solutions.wisely.corda.nms.io

import net.corda.core.serialization.SerializationContext
import net.corda.core.serialization.SerializationContext.UseCase
import net.corda.core.serialization.SerializationFactory
import net.corda.core.serialization.internal.SerializationEnvironment
import net.corda.core.serialization.internal.nodeSerializationEnv
import net.corda.serialization.internal.AMQP_P2P_CONTEXT
import net.corda.serialization.internal.CordaSerializationMagic
import net.corda.serialization.internal.SerializationFactoryImpl
import net.corda.serialization.internal.amqp.AbstractAMQPSerializationScheme
import net.corda.serialization.internal.amqp.SerializerFactory
import net.corda.serialization.internal.amqp.amqpMagic


object SerializationEngine {
    fun init() {
        if (nodeSerializationEnv == null) {
            val classloader = this.javaClass.getClassLoader()
            val serializationFactoryImpl = SerializationFactoryImpl()
            serializationFactoryImpl.registerScheme(object : AbstractAMQPSerializationScheme(listOf()) {
                override fun rpcClientSerializerFactory(context: SerializationContext): SerializerFactory {
                    throw UnsupportedOperationException()
                }

                override fun rpcServerSerializerFactory(context: SerializationContext): SerializerFactory {
                    return this.rpcClientSerializerFactory(context)
                }

                override fun canDeserializeVersion(magic: CordaSerializationMagic, target: UseCase): Boolean {
                    return magic == amqpMagic && target == UseCase.P2P
                }
            })

            nodeSerializationEnv =
                SerializationEnvironment.with(
                    serializationFactoryImpl as SerializationFactory,
                    AMQP_P2P_CONTEXT.withClassLoader(classloader),
                    null,
                    null,
                    null,
                    null,
                    null
                )
        }
    }
}