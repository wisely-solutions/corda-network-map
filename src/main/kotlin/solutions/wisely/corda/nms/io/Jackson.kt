package solutions.wisely.corda.nms.io

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import java.io.File
import java.io.InputStream

object Jackson {
    private val objectMapper = ObjectMapper()
        .registerKotlinModule()

    fun <T> read (input: InputStream, type: Class<T>) : T {
        return objectMapper.readValue(input, type)
    }

    inline fun <reified T> read (input: InputStream): T {
        return read(input, T::class.java)
    }
}

inline fun <reified T> File.json () = Jackson.read<T>(this.inputStream())