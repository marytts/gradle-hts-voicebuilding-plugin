package de.dfki.mary.htsvoicebuilding.stages.task.gv

// Inject
import javax.inject.Inject;

// Worker import
import org.gradle.workers.*;

// Gradle task related
import org.gradle.api.Action;
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.*


/**
 *  Definition of the task type to generate spectrum, f0 and aperiodicity using world vocoder
 *
 */
public class GenerateGVFullContextTask extends DefaultTask {

    @InputFile
    final RegularFileProperty list_file = newInputFile()

    @InputFile
    final RegularFileProperty average_file = newInputFile()

    @InputFile
    final RegularFileProperty vfloor_file = newInputFile()

    /** The directory containing the spectrum files */
    @OutputFile
    final RegularFileProperty model_file = newOutputFile()


    /**
     *  The actual generateion method
     *
     */
    @TaskAction
    public void generate() {

        // Get average informations into head and tail variables
        def found = false
        def head = ""
        def tail = ""
        average_file.getAsFile().get().eachLine { line ->
            if (line.indexOf("~h") >= 0) {
                found = true
            } else if (found) {
                tail += line + "\n"
            } else {
                head += line + "\n"
            }
        }

        // Adding vFloor to head
        vfloor_file.getAsFile().get().eachLine { line ->
            head += line + "\n"
        }

        // Generate full context average model
        def full_mmf = model_file.getAsFile().get()
        full_mmf.write(head)
        list_file.getAsFile().get().eachLine { line ->
            full_mmf.append("~h \"$line\"\n")
            full_mmf.append(tail)
        }
    }
}
