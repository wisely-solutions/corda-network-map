package solutions.wisely.corda.nms

import org.springframework.boot.Banner
import org.springframework.boot.ResourceBanner
import org.springframework.boot.WebApplicationType
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.core.io.ClassPathResource
import solutions.wisely.corda.nms.crypto.CertificateUtils
import solutions.wisely.corda.nms.io.SerializationEngine

fun main(vararg args: String) {
    SerializationEngine.init()
    CertificateUtils.init()
    val app = SpringApplicationBuilder(Application::class.java)
        .bannerMode(Banner.Mode.CONSOLE)
        .banner(ResourceBanner(ClassPathResource("banner.txt")))
        .build()
    app.webApplicationType = WebApplicationType.SERVLET
    app.run(*args)
}


@SpringBootApplication(scanBasePackages = ["solutions.wisely.corda.nms"])
class Application