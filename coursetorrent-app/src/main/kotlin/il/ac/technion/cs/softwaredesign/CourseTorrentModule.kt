package il.ac.technion.cs.softwaredesign

import com.google.inject.Guice
import dev.misfitlabs.kotlinguice4.KotlinModule
import dev.misfitlabs.kotlinguice4.getInstance
import java.nio.charset.Charset

class CourseTorrentModule : KotlinModule() {
    override fun configure() {
        //TODO implement what should be implemented
        val injector = Guice.createInjector(SimpleDBModule())
        bind<SimpleDB>().toInstance(injector.getInstance<SimpleDB>())
    }
}