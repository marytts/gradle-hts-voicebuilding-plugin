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


import de.dfki.mary.utils.StandardTask
import de.dfki.mary.htsvoicebuilding.DataFileFinder

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.xml.*

class GlobalVarianceStages
{
    public static void addTasks(Project project)
    {
        project.task('generateGVProto', dependsOn:'prepareEnvironment')
        {
            outputs.files project.gv_dir + "/proto"
            doLast {
                def nb_stream = 0
                def total_vec_size = 0
                def stream_msd_info = ""
                def stream_vec_size = ""
                project.user_configuration.models.cmp.streams.each { stream ->
                    stream_msd_info += " 0"
                    stream_vec_size += " " + (stream.order + 1)
                    total_vec_size += (stream.order + 1)
                    nb_stream += 1
                }

                def binding = [
                    project:project,
                    GLOBALVECSIZE:total_vec_size,
                               NBSTREAM:nb_stream,
                               STREAMMSDINFO:stream_msd_info,
                               STREAMVECSIZE: stream_vec_size
                ]

                // Now adapt the proto template
                project.copy {
                    from project.template_dir
                    into project.gv_dir

                    include 'protogv'
                    rename {file -> 'proto'}


                    expand(binding)
                }
            }
        }


        // TODO: look if we can parallelize
        project.task('generateStateForceAlignment', type: StandardTask, dependsOn:"trainMonophoneMMF")
        {
            output = project.gv_fal_dir + "/state"


            def output_files = []
            (new File(DataFileFinder.getFilePath(project.user_configuration.data.list_files))).eachLine{ cur_file ->
                def basename = (new File(cur_file)).name
                def label = ""
                def filename = output.toString() + "/" + basename + ".lab"
                output_files.add(filename.toString())
            }
            outputs.files(output_files)

            doLast {
                project.hts_wrapper.HSMMAlign(project.train_scp,
                                              project.mono_list_filename,
                                              project.mono_mlf_filename,
                                              project.cmp_model_dir + "/monophone.mmf",
                                              project.dur_model_dir + "/monophone.mmf",
                                              output.toString(),
                                              true)
            }
        }


        // TODO: look if we can parallelize
        project.task('forceAlignment', type: StandardTask, dependsOn:"generateStateForceAlignment")
        {
            output = project.gv_fal_dir + "/phone"

            doLast {

                def id_last_state = project.settings.models.global.nb_emitting_states + 1
                withPool(project.nb_proc)
                {
                    def file_list = (new File(DataFileFinder.getFilePath(project.user_configuration.data.list_files))).readLines() as List
                    file_list.eachParallel { cur_file ->
                        def state_file = new File(project.tasks.generateStateForceAlignment.output.toString() + "/${cur_file}.lab")
                        def phone_file = new File(output.toString() + "/${cur_file}.lab")


                        def start = 0
                        state_file.eachLine { line ->
                            def val = line.split()
                            def m = val[2] =~ /[^-]*-([^+]*)[+].*\[([0-9]*)\]$/

                            def label = m[0][1]
                            def state = Integer.parseInt(m[0][2])

                            if (state == 2)
                            {
                                start = val[0]
                            }
                            else if (state == id_last_state)
                            {
                                phone_file << "$start ${val[1]} $label\n"
                            }
                        }
                    }
                }
            }
        }

        project.task('GVCoefficientsExtraction', dependsOn:'prepareEnvironment')
        {
            def gv_lab_dir
            if (project.user_configuration.gv.disable_force_alignment) {
                gv_lab_dir = DataFileFinder.getFilePath(project.user_configuration.gv.label_dir)
            } else {
                dependsOn.add("forceAlignment")
                gv_lab_dir = project.gv_fal_dir
            }


            (new File(DataFileFinder.getFilePath(project.user_configuration.data.list_files))).eachLine{ cur_file ->
                def basename = (new File(cur_file)).name
                outputs.files project.gv_data_dir + "/" + basename + ".cmp"
            }

            doLast {
                withPool(project.nb_proc)
                {
                    def file_list = (new File(DataFileFinder.getFilePath(project.user_configuration.data.list_files))).readLines() as List
                    file_list.eachParallel { cur_file ->
                        def basename = (new File(cur_file)).name
                        println("dealing with $basename.....")
                        def fs = project.user_configuration.signal.frameshift
                        def label = ""
                        def i = 0

                        // Reset environment in case of
                        (new File("$project.buildDir/tmp_" + basename + ".cmp")).delete()

                        project.user_configuration.models.cmp.streams.each { stream ->
                            (new File("$project.buildDir/tmp_" + basename + "." + stream.kind)).delete()

                            // Deal with silences (remove them from the coefficient set)
                            if ((project.user_configuration.gv.nosil) &&
                                (project.user_configuration.gv.silences.size() > 0)) {

                                (new File("$gv_lab_dir/${basename}.lab")).eachLine{ cur_lab ->
                                    def cur_lab_arr = cur_lab.split()
                                    def match_sil = project.user_configuration.gv.silences.findResults { it.toString().equals(cur_lab_arr[2])? it.toString() : null}
                                    if (match_sil.size() == 0) {
                                        project.exec {
                                            def start = Integer.parseInt(cur_lab_arr[0]) / (1.0e4 * fs)
                                            def end = Integer.parseInt(cur_lab_arr[1]) / (1.0e4 * fs)

                                            def bash_cmd = ["bcut", "-s", start.intValue(), "-e", end.intValue(), "-n", stream.order, "+f"]
                                            bash_cmd += [DataFileFinder.getFilePath(stream.coeffDir + "/" + basename, stream.kind)]
                                            bash_cmd += [">>","$project.buildDir/tmp_" + basename + "." + stream.kind]

                                            commandLine  ("bash", "-c", bash_cmd.join(" "))
                                        }
                                    }
                                }

                            } else {
                                project.copy {
                                    from DataFileFinder.getFilePath(stream.coeffDir)
                                    to "$project.buildDir"

                                    include basename + "." + stream.kind // FIXME: hyp. kind = extension
                                    rename {file -> "tmp." + stream.kind}
                                }
                            }

                            project.exec {
                                def bash_cmd = ["x2x", "+fa","$project.buildDir/tmp_" + basename + "." + stream.kind, "|"]
                                if (stream.is_msd) {
                                    bash_cmd += ["grep", "-v", "'1e+10'", "|"]
                                }
                                bash_cmd +=  ["x2x", "+af", "|"]
                                bash_cmd += ["vstat", "-d", "-n", stream.order, "-o", "2", ">>","$project.buildDir/tmp_" + basename + ".cmp"]

                                commandLine ("bash", "-c",  bash_cmd.join(" "))
                            }

                            // Clean
                            (new File("$project.buildDir/tmp_" + basename + "." + stream.kind)).delete()

                            i += 4 * (stream.order+1)
                        }

                        // Add HTK Header
                        project.exec {
                            def bash_cmd = ["perl", "$project.buildDir/tmp/utils/addhtkheader.pl", fs, i, 9]
                            bash_cmd += ["$project.buildDir/tmp_" + basename + ".cmp", ">",  project.gv_data_dir + "/" + basename + ".cmp"]
                            commandLine("bash", "-c", bash_cmd.join(" "))
                        }

                        // ugly clean
                        (new File("$project.buildDir/tmp_" + basename + ".cmp")).delete()
                    }
                }
            }
        }

        project.task('generateGVEnvironment', dependsOn: 'GVCoefficientsExtraction')
        {
            // FIXME: add inputs
            outputs.files project.mlf_dir + "/gv.mlf", project.list_dir + "/gv.list"

            // Lists + CMP + SCP
            doLast {
                def model_set = new HashSet()
                def gv_scp_file = new File(project.gv_scp_dir + "/train.scp")
                gv_scp_file.write("") // FIXME: ugly way to reinit the file

                (new File(DataFileFinder.getFilePath(project.user_configuration.data.list_files))).eachLine{ cur_file ->
                    def basename = (new File(cur_file)).name
                    def label = ""
                    def i = 0

                    // Check for NAN
                    def is_nan = false // FIXME: find a way to check directly in gradle
                    if (is_nan) {
                        // FIXME : failing !
                    } else {

                        // Generate lab
                        def cur_full_lab_file = new File(DataFileFinder.getFilePath(project.user_configuration.data.full_lab_dir + "/" + basename, "lab"))
                        def cur_gv_lab_file = new File(project.gv_lab_dir + "/" + basename + ".lab")
                        cur_gv_lab_file.write("")

                        def line
                        cur_full_lab_file.withReader { line = it.readLine() }
                        def line_arr = line =~ /^[ \t]*([0-9]+)[\t ]+([0-9]+)[ \t]+(.+)/
                        cur_gv_lab_file.append(line_arr[0][3]+"\n")

                        // Update model set
                        model_set.add(line_arr[0][3])

                        // Add the file to the list
                        gv_scp_file.append(project.gv_data_dir + "/" + basename + ".cmp\n")
                    }
                }

                // Generate list
                def list_file = new File(project.list_dir + "/gv.list")
                if (project.user_configuration.gv.cdgv) {
                    list_file.write(model_set.join("\n"))
                } else {
                    list_file.write("gv")
                }

                // Generate MLF
                def mlf_file = new File(project.mlf_dir + "/gv.mlf")
                mlf_file.write("#!MLF!#\n")
                mlf_file.append('"*/*.lab" -> "' + project.gv_lab_dir + '"\n')
            }
        }

        /**************************************************************************************************************
         ** Train base models
         **************************************************************************************************************/
        project.task('generateGVAverage', dependsOn:['generateGVProto', 'generateGVEnvironment'])
        {
            inputs.files project.gv_dir + "/proto", project.mlf_dir + "/gv.mlf", project.list_dir + "/gv.list"
            outputs.files project.gv_dir + "/average.mmf"
            doLast {
                project.hts_wrapper.HCompV(project.gv_scp_dir + "/train.scp",
                                           project.gv_dir + "/proto",
                                           project.gv_dir + "/average.mmf",
                                           project.gv_dir)
            }
        }


        project.task('generateGVFullcontext', dependsOn:'generateGVAverage')
        {
            inputs.files project.gv_dir + "/average.mmf"
            outputs.files project.gv_dir + "/fullcontext.mmf.noembedded.gz"

            doLast {

                // Get average informations into head and tail variables
                def found = false
                def head = ""
                def tail = ""
                (new File(project.gv_dir + "/average.mmf")).eachLine { line ->
                    if (line.indexOf("~h") >= 0) {
                        found = true
                    } else if (found) {
                        tail += line + "\n"
                    } else {
                        head += line + "\n"
                    }
                }

                // Adding vFloor to head
                (new File(project.gv_dir + "/vFloors")).eachLine { line ->
                    head += line + "\n"
                }

                // Generate full context average model
                def full_mmf = new File(project.gv_dir + "/fullcontext.mmf")
                full_mmf.write(head)
                (new File(project.list_dir + "/gv.list")).eachLine { line ->
                    full_mmf.append("~h \"$line\"\n")
                    full_mmf.append(tail)
                }
            }
        }

        project.task('trainGVFullcontext', dependsOn:'generateGVFullcontext')
        {
            inputs.files project.gv_dir + "/fullcontext.mmf"
            outputs.files project.gv_dir + "/fullcontext.mmf.embedded.gz"

            doLast {
                project.hts_wrapper.HERestGV(project.gv_scp_dir + "/train.scp",
                                             project.list_dir + "/gv.list",
                                             project.mlf_dir + "/gv.mlf",
                                             project.gv_dir + "/fullcontext.mmf",
                                             project.gv_dir,
                                             [
                                                 "-C", project.non_variance_config_filename,
                                                 "-s", project.gv_dir + "/stats",
                                                 "-w", 0
                                             ])
            }
        }


        /**************************************************************************************************************
         ** Clustering
         **************************************************************************************************************/
        project.task("generateGVClustered", dependsOn:"trainGVFullcontext") {

            // logging.captureStandardOutput LogLevel.INFO
            // logging.captureStandardError LogLevel.ERROR

            def question_file = (new File (DataFileFinder.getFilePath(project.user_configuration.data.question_file_gv)))
            inputs.files project.gv_dir + "/fullcontext.mmf", question_file
            outputs.files project.gv_dir + "/clustered.mmf.noembedded.gz"

            doLast {

                project.copy {
                    from project.gv_dir
                    into project.gv_dir
                    include "fullcontext.mmf"
                    rename {file -> "clustered.mmf"}
                }


                def s = 1
                project.user_configuration.models.cmp.streams.each { stream ->

                    // FIXME: what to do with this stuff ?
                    //             make_edfile_state_gv( $type, $s );

                    //   2. generate HHEd scripts
                    project.copy {

                        from project.template_dir
                        into project.hhed_script_dir
                        include 'cxc.hed'
                        rename {file -> "cxc_gv_" + stream.name + ".hed"}

                        def questions = question_file.text
                        def streamline = "TB " + stream.gv.thr + " gv_" + stream.name +  "_ {*.state[2].stream[$s]}\n"
                        def binding = [
                        GAM : stream.gv.gam,
                        STATSFILE:project.gv_dir + "/stats",
                        QUESTIONS:questions,
                        STREAMLINE:streamline,
                        OUTPUT:project.gv_dir + "/" + stream.name + ".inf"
                        ]

                        expand(binding)
                    }

                    //   3. build the decision tree
                    def params = []
                    if (stream.gv.thr == 0) {
                        params += ["-m", "-a", stream.gv.mdlf]
                    }

                    project.hts_wrapper.HHEdOnMMF(project.hhed_script_dir + "cxc_gv_" + stream.name + ".hed",
                                                  project.list_dir + "/gv.list",
                                                  project.gv_dir + "/clustered.mmf",
                                                  project.gv_dir + "/clustered.mmf",
                                                  params)
                    s += 1
                }
            }
        }

        project.task("trainGVClustered", dependsOn:"generateGVClustered")
        {
            inputs.files project.gv_dir + "/clustered.mmf"
            outputs.files project.gv_dir + "/clustered.mmf.embbeded.gz"
            doLast {
                project.hts_wrapper.HERestGV(project.gv_scp_dir + "/train.scp",
                                             project.list_dir + "/gv.list",
                                             project.mlf_dir + "/gv.mlf",
                                             project.gv_dir + "/clustered.mmf",
                                             project.gv_dir,
                                             [])
            }
        }

        // TODO: finish it !
        project.task("averageGV2clustered", dependsOn:"generateGVAverage")
        {
            // FIXME: add inputs.files
            inputs.files project.gv_dir + "/average.mmf", project.gv_dir + "/vFloors"
            outputs.files project.gv_dir + "/clustered.mmf"
            doLast {

                // Get average informations into head and tail variables
                def found_head_end = false
                def found_mid_end = false
                def found_pdf_end = false
                def head = ""
                def tail = ""
                def mid = ""
                def pdf = []
                def s = 1

                (new File(project.gv_dir + "/average.mmf")).eachLine { line ->
                    if (line.indexOf("~h") >= 0) {
                        found_head_end = true

                        // Filling the head
                    } else if (!found_head_end) {
                        head += line + "\n"

                        // Filling the mid part
                    } else if ((found_head_end) && (!found_mid_end)) {

                        if (line.indexOf("<STREAM>") >= 0) {
                            found_mid_end = true
                            pdf[s] = ""
                        } else {
                            mid += line + "\n"
                        }

                        // Filling the pdf part
                    } else if ((found_mid_end) && (!found_pdf_end)) {

                        if (line.indexOf("<TRANSP>") >= 0) {
                            found_pdf_end = true
                        } else if (line.indexOf("<STREAM>") >= 0) {
                            s += 1
                            pdf[s] = ""
                        } else {
                            pdf[s] += line + "\n"
                        }
                        // Filling the tail part
                    } else if (found_pdf_end) {
                    } else {
                        // FIXME: error
                    }
                }

                // Adding vFloor to head
                (new File(project.gv_dir + "/vFloors")).eachLine { line ->
                    head += line + "\n"
                }


                // Generate clustered model from average data
                def clustered_mmf = new File(project.gv_dir + "/clustered.mmf")
                clustered_mmf.write(head)
                s = 1
                project.user_configuration.models.cmp.streams.each { stream ->
                    clustered_mmf.append("~p \"gv_" + stream.name + "_1\"\n")
                    clustered_mmf.append("<STREAM> $s\n")
                    clustered_mmf.append(pdf[s-1])
                    s += 1
                }

                clustered_mmf.append("~h \"gv\"\n")
                clustered_mmf.append(mid)
                s = 1
                project.user_configuration.models.cmp.streams.each { stream ->
                    clustered_mmf.append("<STREAM> $s\n")
                    clustered_mmf.append("~p \"gv_" + stream.name + "_1\"\n")
                    s += 1
                }
                clustered_mmf.append(tail)
            }
        }


        project.task("trainGV")
        {
            if (project.user_configuration.gv.cdgv) {
                dependsOn "trainGVClustered"
            } else {
                dependsOn "averageGV2clustered"
            }
        }
    }
}
