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

import de.dfki.mary.htsvoicebuilding.DataFileFinder

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.xml.*

class InitialisationStages {

    /****************************************************************************************
     **
     ****************************************************************************************/
    public static void addTasks(Project project)
    {
        project.task('generateSCPFile', dependsOn: 'prepareEnvironment')
        {
            def train_scp_fh = new File(project.train_scp)
            inputs.files project.user_configuration.data.list_files
            outputs.files train_scp_fh

            doLast {
                (new File(DataFileFinder.getFilePath(project.user_configuration.data.list_files))).eachLine{ cur_file ->
                    def basename = (new File(cur_file)).name
                    train_scp_fh << "$project.buildDir/cmp/" // FIXME: be more clever for directory
                    train_scp_fh << basename << ".cmp"
                    train_scp_fh << "\n"
                }
            }

        }

        project.task('generateMonophoneList', dependsOn: 'generateSCPFile')
        {
            outputs.files project.mono_mlf_filename, project.mono_list_filename

            // 1. Generate MLF
            def mlf_file = new File(project.mono_mlf_filename)
            mlf_file.write("#!MLF!#\n")
            mlf_file.append('"*/*.lab" -> "' + DataFileFinder.getFilePath(project.user_configuration.data.mono_lab_dir) +'"')

            // 2. From known mono_lab_dir and train scp infos
            def model_set = new HashSet()
            (new File(DataFileFinder.getFilePath(project.user_configuration.data.list_files))).eachLine{ cur_file ->
                def basename = (new File(cur_file)).name
                (new File(DataFileFinder.getFilePath(project.user_configuration.data.mono_lab_dir + "/" + basename + ".lab"))).eachLine { line ->

                    def line_arr = line =~ /^[ \t]*([0-9]+)[ \t]+([0-9]+)[ \t]+(.+)/
                    model_set.add(line_arr[0][3])
                }
            }
            (new File(project.mono_list_filename)).write(model_set.join("\n"))
        }

        project.task('generatePrototype', dependsOn: 'prepareEnvironment')
        {
            outputs.files "$project.proto_dir/proto"

            doLast {
                // Global informations
                def total_nb_states = project.user_configuration.models.global.nb_emitting_states + 2
                def nb_stream = 0
                def total_vec_size = 0
                def stream_msd_info = ""
                def stream_vec_size = ""
                def sweights = ""
                project.user_configuration.models.cmp.streams.each { stream ->
                    if (stream.is_msd) {
                        for (i in 1..stream.winfiles.size()) {
                            stream_msd_info += " 1"
                            stream_vec_size += " 1"
                            sweights += " " + stream.weight
                        }
                        total_vec_size += (stream.order + 1) * stream.winfiles.size()
                        nb_stream += stream.winfiles.size()
                    } else {
                        stream_msd_info += " 0"
                        stream_vec_size += " " + (stream.order + 1) * stream.winfiles.size()
                        sweights += " " + stream.weight
                        total_vec_size += (stream.order + 1) * stream.winfiles.size()
                        nb_stream += 1
                    }
                }

                // Now adapt the proto template
                def binding = [
                project:project,
                SWEIGHTS:sweights,
                GLOBALVECSIZE:total_vec_size,
                NBSTREAM:nb_stream,
                STREAMMSDINFO:stream_msd_info,
                STREAMVECSIZE: stream_vec_size,
                NBSTATES:total_nb_states,
                ]

                // Copy
                project.copy {
                    from project.template_dir
                    into project.proto_dir

                    include "proto"

                    expand(binding)
                }
            }
        }


        project.task('generateConfigurationFiles', dependsOn: 'prepareEnvironment')
        {
            inputs.files project.config_dir
            outputs.files project.train_config_filename

            // train.cfg
            def nbstream = 0
            def vfloorvalues = ""
            project.user_configuration.models.cmp.streams.each { stream ->
                if (stream.is_msd) {
                    nbstream += stream.winfiles.size()
                    for (i in 0..(stream.winfiles.size()-1)) {
                        vfloorvalues += " " + stream.vflr
                    }
                } else {
                    nbstream += 1
                    vfloorvalues += " " + stream.vflr
                }
            }

            def binding = [
            VFLOORDUR : project.user_configuration.models.dur.vflr * 100,
            MAXDEV : project.user_configuration.settings.training.maxdev,
            MINDUR : project.user_configuration.settings.training.mindur,
            NBSTREAM : nbstream,
            VFLOORVALUES: vfloorvalues
            ]

            project.copy {
                from project.template_dir
                into project.config_dir

                include "train.cfg"
                rename { file -> (new File(project.train_config_filename)).name }
                expand(binding)
            }

            // nvf.cfg
            project.copy {
                from project.template_dir
                into project.config_dir

                include "nvf.cfg"
                rename { file -> (new File(project.non_variance_config_filename)).name }
            }

            // Model tying (cmp)
            project.user_configuration.models.cmp.streams.each { stream ->
                binding = [mocc : stream.mocc]
                project.copy {
                    from project.template_dir
                    into project.config_dir

                    include "mocc.cfg"
                    rename { file -> stream.name + ".cfg" }

                    expand(binding)
                }
            }

            // Model tying (dur)
            binding = [mocc : project.user_configuration.models.dur.mocc]
            project.copy {
                from project.template_dir
                into project.config_dir

                include "mocc.cfg"
                rename { file -> "dur.cfg" }

                expand(binding)
            }
        }

        project.task('initModels', dependsOn:['generatePrototype', 'generateSCPFile', 'generateMonophoneList', 'generateConfigurationFiles'])
        {
            // logging.captureStandardOutput LogLevel.INFO
            // logging.captureStandardError LogLevel.ERROR

            def cmp_vfloors = project.cmp_model_dir + "/vFloors"
            def cmp_init = project.cmp_model_dir + "/init.mmf"
            def cmp_average = project.cmp_model_dir + "/average.mmf"
            def dur_vfloors = project.dur_model_dir + "/vFloors"
            def dur_init = project.dur_model_dir + "/init.mmf"
            def dur_average = project.dur_model_dir + "/average.mmf"

            inputs.files "$project.proto_dir/proto"
            outputs.files cmp_vfloors, cmp_init, cmp_average, dur_vfloors, dur_init, dur_average


            doLast {
                // CMP parts
                //   1. Get average model
                project.hts_wrapper.HCompV(project.train_scp,
                                           "$project.proto_dir/proto",
                                           "average.mmf",
                                           project.cmp_model_dir)

                //   2. Get Init model
                def cur_file = new File("$project.proto_dir/proto")
                def header = cur_file.readLines()[0]

                cur_file = new File(cmp_vfloors)

                def init_file = new File(cmp_init)
                init_file.write(header + "\n" + cur_file.text)


                // DUR part
                //    1. vfloor file
                def engine = new groovy.text.SimpleTemplateEngine()
                def vfloor_template = (new File("$project.template_dir/vfloordur")).text // FIXME: update template path
                def content = ""
                for (i in 1..project.user_configuration.models.global.nb_emitting_states) {
                    def variance = project.user_configuration.models.dur.vflr
                    variance *= project.user_configuration.models.dur.initvar

                    def binding = [
                    STATEID:i,
                    VARIANCE:variance
                    ]
                    content += engine.createTemplate(vfloor_template).make(binding)
                }
                def vfloor_dur_file = new File(dur_vfloors)
                vfloor_dur_file.write(content)

                //   2. average file (TODO: move that into the template and deal properly with the template !)
                content = ""
                for (i in 1..project.user_configuration.models.global.nb_emitting_states) {
                    content += "\t\t<STREAM> $i\n"
                    content += "\t\t\t<MEAN> 1\n"
                    content += "\t\t\t\t" + project.user_configuration.models.dur.initmean + "\n"
                    content += "\t\t\t<VARIANCE> 1\n"
                    content += "\t\t\t\t" + project.user_configuration.models.dur.initvar + "\n"

                }

                def binding = [
                NBSTATES:project.user_configuration.models.global.nb_emitting_states,
                STATECONTENT:content,
                NAME:"average.mmf"
                ]
                def average_template = (new File("$project.template_dir/average_dur.mmf")).text
                def average_dur_file = new File(dur_average)
                average_dur_file.write(engine.createTemplate(average_template).make(binding).toString())
            }
        }
    }
}
