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


// Task imports
import de.dfki.mary.htsvoicebuilding.stages.task.InitPhoneModelsTask

class MonophoneStages {

    /****************************************************************************************
     **
     ****************************************************************************************/
    public static void addTasks(Project project)
    {
        project.task('initialiseMonophoneModels', type: InitPhoneModelsTask)
        {
            // Some global configuration
            use_daem = project.configuration.user_configuration.settings.daem.use

            // Meta files
            scp_file = project.generateSCPFile.scp_file
            mlf_file = project.generateMonoMLF.mlf_file
            list_file = project.generateMonophoneList.list_file

            // Prototype files
            prototype_file = project.generatePrototype.prototype_file

            // CMP related input files
            vfloor_cmp_file = project.initModels.vfloor_cmp_file
            average_cmp_file = project.initModels.average_cmp_file
            init_cmp_file = project.initModels.init_cmp_file

            // Duration related input files
            // vfloor_dur_file = project.initModels.vfloor_dur_file
            average_dur_file = project.initModels.average_dur_file
            // init_dur_file = project.initModels.init_dur_file

            // Output cmp files
            cmp_hinit_dir = new File("$project.cmp_model_dir/HInit/")
            cmp_hrest_dir = new File("$project.cmp_model_dir/HRest/")

            // Output dur files
            dur_hrest_dir = new File("$project.dur_model_dir/HRest/")
        }

        project.task('generateMonophoneMMF', dependsOn: 'initialiseMonophoneModels')
        {
            doLast {
                // FIXME: use the list !
                def monophone_set = new HashSet()
                (new File(project.configuration.user_configuration.data.list_files)).eachLine{ cur_file ->
                    def basename = (new File(cur_file)).name

                    // Analyse file
                    (new File(project.configuration.user_configuration.data.mono_lab_dir + "/" + basename + ".lab")).eachLine { cur_lab ->
                        def line_arr = cur_lab =~ /^[ \t]*([0-9]+)[ \t]+([0-9]+)[ \t]+(.+)/
                        monophone_set.add(line_arr[0][3])
                    }
                }


                // Generate HHEd script
                project.copy {
                    from project.template_dir
                    into project.hhed_script_dir

                    include 'lvf.hed'
                    rename { file -> "lvf.cmp.hed"}
                    def binding = [
                        STARTSTATE:2,
                        ENDSTATE:project.configuration.user_configuration.models.global.nb_emitting_states+1,
                        VFLOORFILE:project.cmp_model_dir + "/vFloors",
                        NB_STREAMS: project.configuration.user_configuration.models.cmp.streams.size()
                    ]

                    expand(binding)
                }


                // Generate HHEd script
                def content = "// Load variance flooring macro\n"
                content += "FV \"" + project.dur_model_dir + "/vFloors\""
                (new File(project.hhed_script_dir + "/lvf.dur.hed")).write(content)


                project.configurationVoiceBuilding.hts_wrapper.HHEdOnDir(project.hhed_script_dir + "/lvf.cmp.hed", project.mono_list_filename,
                                                                         project.cmp_model_dir + "/HRest", project.cmp_model_dir + "/monophone.mmf")


                project.configurationVoiceBuilding.hts_wrapper.HHEdOnDir(project.hhed_script_dir + "/lvf.dur.hed", project.mono_list_filename,
                                                                         project.dur_model_dir + "/HRest", project.dur_model_dir + "/monophone.mmf")


            }
        }

        project.task('trainMonophoneMMF', dependsOn: 'generateMonophoneMMF')
        {
            doLast {
                if (project.configuration.user_configuration.settings.daem.use) {
                    for (i in 1..project.configuration.user_configuration.settings.daem.nIte) {
                        for (j in 1..project.configuration.user_configuration.settings.training.nIte) {
                            //
                            def k = j + (i-1) ** project.configuration.user_configuration.settings.training.nIte
                            println("\n\nIteration $k of Embedded Re-estimation")

                            k = (i / project.configuration.user_configuration.settings.daem.nIte) ** project.configuration.user_configuration.settings.daem.alpha

                            project.configurationVoiceBuilding.hts_wrapper.HERest(project.train_scp,
                                                                                  project.mono_list_filename,
                                                                                  project.mono_mlf_filename,
                                                                                  project.cmp_model_dir + "/monophone.mmf",
                                                                                  project.dur_model_dir + "/monophone.mmf",
                                                                                  project.cmp_model_dir,
                                                                                  project.dur_model_dir
                                                                                  ["-k", k])
                        }
                    }
                } else {
                    for (i in 1..project.configuration.user_configuration.settings.training.nIte) {
                        //
                        println("\n\nIteration $i of Embedded Re-estimation")

                        project.configurationVoiceBuilding.hts_wrapper.HERest(project.train_scp,
                                                                              project.mono_list_filename,
                                                                              project.mono_mlf_filename,
                                                                              project.cmp_model_dir + "/monophone.mmf",
                                                                              project.dur_model_dir + "/monophone.mmf",
                                                                              project.cmp_model_dir,
                                                                              project.dur_model_dir,
                                                                              [])
                    }
                }
            }
        }
    }
}
