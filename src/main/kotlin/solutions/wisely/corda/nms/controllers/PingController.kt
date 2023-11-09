package solutions.wisely.corda.nms.controllers

import java.nio.charset.Charset
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RestController

@RestController
class PingController {
    @RequestMapping(path = ["/ping"], method = [RequestMethod.GET])
    fun ping(): ByteArray {
        val string = "OK"
        val charset: Charset = Charsets.UTF_8
        return string.toByteArray(charset)
    }
}