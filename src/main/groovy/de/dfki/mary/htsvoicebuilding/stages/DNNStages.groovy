package de.dfki.mary.htsvoicebuilding.stages

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.MavenPlugin
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.JavaExec
import org.gradle.api.tasks.bundling.Zip

import static groovyx.gpars.GParsPool.runForkJoin
import static groovyx.gpars.GParsPool.withPool

import de.dfki.mary.htsvoicebuilding.HTSWrapper
import de.dfki.mary.htsvoicebuilding.DataFileFinder

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.xml.*

class DNNStages
{
    public static void addTasks(Project project)
    {
        def dnn_output_dir = "$project.buildDir/DNN/"
        project.task("makeFeatures", dependsOn: "forceAlignment")
        {

            def output_dir = "$dnn_output_dir/ffi"
            (new File(output_dir)).mkdirs()
            def mkf_script_file = "$project.utils_dir/makefeature.pl"; // TODO
            def qconf = (new File(DataFileFinder.getFilePath(project.user_configuration.settings.dnn.qconf))); // TODO: what is it ?
            def hal_dir = project.gv_fal_dir; // TODO: link
            def val = 1E+3 * project.user_configuration.signal.frameshift;

            outputs.files hal_dir
            doLast {

                withPool(project.nb_proc)
                {
                    def file_list = (new File(DataFileFinder.getFilePath(project.user_configuration.data.list_files))).readLines() as List
                    file_list.eachParallel { cur_file ->
                        java.lang.String command = "perl $mkf_script_file $qconf $val $hal_dir/${cur_file}.lab | x2x +af > $output_dir/${cur_file}.ffi".toString()
                        HTSWrapper.executeOnShell(command)
                    }
                }
            }
        }

        project.task("makeDataSCP") {
            dependsOn "makeFeatures"
            def scp = new File("$dnn_output_dir/train_dnn.scp")
            scp.text = ""
            outputs.files scp
            doLast {
                def ffo_dir = "" // TODO: link
                (new File(DataFileFinder.getFilePath(project.user_configuration.data.list_files))).eachLine { cur_file ->
                    scp << "$dnn_output_dir/ffi/ $ffo_dir" + System.getProperty("line.separator2")
                }
            }

        }

        project.task("trainDNN")
        {
            dependsOn "makeDataSCP"
        }
    }
}
