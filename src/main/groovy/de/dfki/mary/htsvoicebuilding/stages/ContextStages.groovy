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

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.xml.*

class ContextStages
{
    public static void addTasks(Project project)
    {
        project.task('generateFulllist', dependsOn:'prepareEnvironment')
        {
            outputs.files project.full_mlf_filename, project.full_list_filename

            doLast {
                // 1. Generate MLF
                def mlf_file = new File(project.full_mlf_filename)
                mlf_file.write("#!MLF!#\n")
                mlf_file.append('"*/*.lab" -> "' + project.configuration.user_configuration.data.full_lab_dir +'"')

                // 2. From known full_lab_dir and train scp infos
                def model_set = new HashSet()
                (new File(project.configuration.user_configuration.data.list_files)).eachLine{ cur_file ->
                    def basename = (new File(cur_file)).name
                    (new File(project.configuration.user_configuration.data.full_lab_dir + "/" + basename + ".lab")).eachLine { line ->

                        def line_arr = line =~ /^[ \t]*([0-9]+)[ \t]+([0-9]+)[ \t]+(.+)/
                        model_set.add(line_arr[0][3])
                    }
                }
                (new File(project.full_list_filename)).write(model_set.join("\n"))
            }
        }

        project.task('generateFullcontextFromMonophone', dependsOn:['generateFulllist', 'trainMonophoneMMF'])
        {
            inputs.files project.full_mlf_filename, project.full_list_filename, "$project.buildDir/achievedstages/trainMonophoneMMF"
            outputs.files "$project.buildDir/achievedstages/generateFullcontextFromMonophone"


            doLast {

                // CMP
                def content = "// copy monophone models to fullcontext ones\n"
                content += "CL \"" + project.full_list_filename +  "\"\n\n"
                content += "// tie state transition probability\n"
                (new File(project.mono_list_filename)).eachLine { phone ->
                    if (phone != "") {
                        content += "TI T_$phone {*-$phone+*.transP}\n"
                    }
                }

                (new File(project.hhed_script_dir + "/m2f.cmp.hed")).write(content)

                project.configuration.hts_wrapper.HHEdOnMMF(project.hhed_script_dir + "/m2f.cmp.hed",
                                                            project.mono_list_filename,
                                                            project.cmp_model_dir + "/monophone.mmf",
                                                            project.cmp_model_dir + "/fullcontext.mmf.0",
                                                            [])


                // Duration
                content = "// copy monophone models to fullcontext ones\n"
                content += "CL \"" + project.full_list_filename +  "\"\n\n"
                content += "// tie state transition probability\n"
                (new File(project.mono_list_filename)).eachLine { phone ->
                    if (phone != "") {
                        content += "TI T_$phone {*-$phone+*.transP}\n"
                    }
                }
                (new File(project.hhed_script_dir + "/m2f.dur.hed")).write(content)

                project.configuration.hts_wrapper.HHEdOnMMF(project.hhed_script_dir + "/m2f.dur.hed",
                                                            project.mono_list_filename,
                                                            project.dur_model_dir + "/monophone.mmf",
                                                            project.dur_model_dir + "/fullcontext.mmf.0",
                                                            [])

                (new File("$project.buildDir/achievedstages/generateFullcontextFromMonophone")).text = "ok"
            }
        }

        project.task('trainFullContext0', dependsOn:['generateFullcontextFromMonophone'])
        {
            inputs.files "$project.buildDir/achievedstages/generateFullcontextFromMonophone"
            outputs.files "$project.buildDir/achievedstages/trainFullContext0"

            doLast {
                for (i in 1..project.configuration.user_configuration.settings.training.nIte)
                    {
                    project.configuration.hts_wrapper.HERest(project.train_scp,
                                                             project.full_list_filename, project.full_mlf_filename,
                                                             project.cmp_model_dir + "/fullcontext.mmf.0",
                                                             project.dur_model_dir + "/fullcontext.mmf.0",
                                                             project.cmp_model_dir,
                                                             project.dur_model_dir,
                                                             ["-C", project.non_variance_config_filename,
                                                              "-s", project.cmp_model_dir + "/stats.0", "-w", 0.0])
                }

                (new File("$project.buildDir/achievedstages/trainFullContext0")).text = "ok"
            }
        }


        for (def cur_clus_it=0; cur_clus_it < project.configuration.user_configuration.settings.training.nb_clustering; cur_clus_it++)
            {
            def local_cur_clus_it = cur_clus_it

            project.task("clusteringCMP" + local_cur_clus_it, dependsOn: "trainFullContext" + local_cur_clus_it)
            {
                inputs.files "$project.buildDir/achievedstages/trainFullContext" + local_cur_clus_it
                inputs.files project.cmp_model_dir + "/stats." + local_cur_clus_it
                outputs.files "$project.buildDir/achievedstages/clusteringCMP" + local_cur_clus_it

                doLast {
                    def project_cur_stream = 1

                    // Prepare parallelism part !
                    def streams = []
                    project.configuration.user_configuration.models.cmp.streams.each { stream ->
                        // FIXME: Define indexes
                        stream.start = project_cur_stream
                        stream.end   = project_cur_stream
                        if (stream.is_msd) {
                            stream.end += stream.winfiles.size() - 1
                        }

                        project_cur_stream = stream.end + 1
                        streams << stream
                    }

                    withPool(project.configuration.nb_proc) {
                        streams.eachParallel() { stream ->
                            // FIXME: Define indexes
                            def end_stream = project_cur_stream
                            def cur_stream = project_cur_stream
                            if (stream.is_msd) {
                                end_stream += stream.winfiles.size() - 1
                            }

                            def streamname = stream.name

                            def questions_file = (new File(project.configuration.user_configuration.data.question_file))

                            //   2. generate HHEd scripts
                            project.copy {

                                from project.template_dir
                                into project.hhed_script_dir
                                include 'cxc.hed'
                                rename {file -> "cxc_" + stream.name + "." + local_cur_clus_it + ".hed"}

                                def questions = questions_file.text
                                def streamline = ""
                                for (i in 2..project.configuration.user_configuration.models.global.nb_emitting_states+1) {
                                    streamline += "TB " + stream.thr + " " + stream.name +  "_s" + i + "_ "
                                    streamline += "{*.state[" + i + "].stream[" + stream.start +  "-" + (stream.end)+ "]}\n"
                                }
                                def binding = [
                                    GAM : sprintf("%03d", stream.gam),
                                    STATSFILE:project.cmp_model_dir + "/stats." + local_cur_clus_it,
                                    QUESTIONS:questions,
                                    STREAMLINE:streamline,
                                    OUTPUT:project.tree_dir + "/" + stream.name + "." + local_cur_clus_it + ".inf"
                                ]

                                expand(binding)
                            }

                            //   3. build the decision tree
                            def params = ["-C", project.config_dir + "/" + stream.name + ".cfg"]

                            if (stream.thr == 0) {
                                params += ["-m", "-a", stream.mdlf]
                            }

                            project.configuration.hts_wrapper.HHEdOnMMF(project.hhed_script_dir + "cxc_" + stream.name + "." + local_cur_clus_it + ".hed",
                                                                        project.full_list_filename,
                                                                        project.cmp_model_dir + "/fullcontext.mmf." + local_cur_clus_it,
                                                                        "$project.cmp_model_dir/clustered.mmf.${stream.name}.$local_cur_clus_it",
                                                                        params)
                        }
                    }

                    // Join (only if more than one stream are used)
                    //   1. copy the first stream models
                    def tmp_stream = project.configuration.user_configuration.models.cmp.streams[0]
                    project.copy {
                        from project.cmp_model_dir
                        into project.cmp_model_dir
                        include "clustered.mmf.${tmp_stream.name}." + local_cur_clus_it
                        rename { file -> "clustered.mmf." + local_cur_clus_it }
                    }

                    //  2. join the other one
                    if (project.configuration.user_configuration.models.cmp.streams.size() > 1) {

                        def join_script = new File(project.hhed_script_dir + "/join.hed." + local_cur_clus_it)
                        join_script.write("")
                        for(def s=0; s<project.configuration.user_configuration.models.global.nb_emitting_states; s++) {
                            def cur_stream = 1
                            project.configuration.user_configuration.models.cmp.streams.each { stream ->

                                def end_stream = cur_stream
                                if (stream.is_msd) {
                                    end_stream += stream.winfiles.size() - 1
                                }

                                if (cur_stream > 1) {
                                    join_script.append(sprintf("JM %s {*.state[%d].stream[%d-%d]}\n",
                                                               "$project.cmp_model_dir/clustered.mmf.${stream.name}.$local_cur_clus_it",
                                                               s+2, cur_stream, end_stream))
                                }

                                cur_stream = end_stream + 1
                            }
                            join_script.append("\n")
                        }

                        project.configuration.hts_wrapper.HHEdOnMMF(project.hhed_script_dir + "/join.hed." + local_cur_clus_it,
                                                                    project.full_list_filename,
                                                                    project.cmp_model_dir + "/clustered.mmf." + local_cur_clus_it,
                                                                    project.cmp_model_dir + "/clustered.mmf." + local_cur_clus_it,
                                                                    [])
                    }

                    (new File("$project.buildDir/achievedstages/clusteringCMP" + local_cur_clus_it)).text = "ok"
                }
            }

            project.task("clusteringDUR" + cur_clus_it, dependsOn: "trainFullContext" + local_cur_clus_it)
            {
                def questions_file = (new File(project.configuration.user_configuration.data.question_file))
                inputs.files questions_file, project.dur_model_dir + "/stats." + local_cur_clus_it
                inputs.files "$project.buildDir/achievedstages/trainFullcontext" + local_cur_clus_it
                outputs.files "$project.buildDir/achievedstages/clusteringDUR" + local_cur_clus_it, project.tree_dir + "/dur." + local_cur_clus_it + ".inf"

                doLast {
                    // Copy stats
                    def cmp_stats_file = new File(project.cmp_model_dir + "/stats." + local_cur_clus_it)
                    def dur_stats_file = new File(project.dur_model_dir + "/stats." + local_cur_clus_it)

                    dur_stats_file.write("")
                    cmp_stats_file.eachLine { line ->
                        def array = line.split()
                        dur_stats_file.append(sprintf("%4d %14s %4d %4d\n",
                                                      Integer.parseInt(array[0]), array[1],
                                                      Integer.parseInt(array[2]), Integer.parseInt(array[2])))
                    }

                    //   1. copy fullcontext.mmf -> clustered.mmf
                    project.copy {
                        from project.dur_model_dir
                        into project.dur_model_dir
                        include  "fullcontext.mmf." + local_cur_clus_it
                        rename {file -> "clustered.mmf." + local_cur_clus_it}
                    }

                    //   2. generate HHEd scripts
                    project.copy {

                        from project.template_dir
                        into project.hhed_script_dir
                        include 'cxc.hed'
                        rename {file -> "cxc_dur.hed." + local_cur_clus_it}

                        def questions = questions_file.text
                        def streamline = "TB " + project.configuration.user_configuration.models.dur.thr + " dur_s2_ {*.state[2].stream[1-5]}"
                        def binding = [
                            GAM : sprintf("%03d", project.configuration.user_configuration.models.dur.gam),
                            STATSFILE:project.dur_model_dir + "/stats." + local_cur_clus_it,
                            QUESTIONS:questions,
                            STREAMLINE:streamline,
                            OUTPUT:project.tree_dir + "/dur." + local_cur_clus_it + ".inf"
                        ]

                        expand(binding)
                    }

                    //   3. build the decision tree
                    def params = ["-C", project.config_dir + "/dur.cfg"]
                    if (project.configuration.user_configuration.models.dur.thr == 0) {
                        params += ["-m", "-a", project.configuration.user_configuration.models.dur.mdlf]
                    }

                    project.configuration.hts_wrapper.HHEdOnMMF(project.hhed_script_dir + "cxc_dur.hed." + local_cur_clus_it,
                                                                project.full_list_filename,
                                                                project.dur_model_dir + "/clustered.mmf." + local_cur_clus_it,
                                                                project.dur_model_dir + "/clustered.mmf." + local_cur_clus_it,
                                                                params)

                    (new File("$project.buildDir/achievedstages/clusteringDUR" + local_cur_clus_it)).text = "ok"
                }
            }

            project.task("trainClusteredModels" + local_cur_clus_it, dependsOn:["clusteringCMP" + cur_clus_it, "clusteringDUR" + cur_clus_it])
            {
                outputs.files "$project.buildDir/achievedstages/clusteringCMP" + local_cur_clus_it, "$project.buildDir/achievedstages/clusteringDUR" + local_cur_clus_it
                outputs.files "$project.buildDir/achievedstages/trainClusteredModels" + local_cur_clus_it

                doLast {

                    for (i in 1..project.configuration.user_configuration.settings.training.nIte) {

                        project.configuration.hts_wrapper.HERest(project.train_scp,
                                                                 project.full_list_filename,
                                                                 project.full_mlf_filename,
                                                                 project.cmp_model_dir + "/clustered.mmf." + local_cur_clus_it,
                                                                 project.dur_model_dir + "/clustered.mmf." + local_cur_clus_it,
                                                                 project.cmp_model_dir,
                                                                 project.dur_model_dir,
                                                                 [])
                    }


                    (new File("$project.buildDir/achievedstages/trainClusteredModels" + local_cur_clus_it)).text = "ok"
                }
            }


            if (local_cur_clus_it  < (project.configuration.user_configuration.settings.training.nb_clustering)) {

                project.task("untyingCMP" + cur_clus_it, dependsOn: "trainClusteredModels" + cur_clus_it) {

                    inputs.files "$project.buildDir/achievedstages/trainClusteredModels" + cur_clus_it
                    outputs.files "$project.buildDir/achievedstages/untyingCMP" + cur_clus_it

                    //  1. Generate hhed script file
                    def cur_stream = 1
                    def cmp_untying_file = new File(project.hhed_script_dir + "/untying_cmp.hhed")

                    cmp_untying_file.write("// untie parameter sharing structure\n")
                    project.configuration.user_configuration.models.cmp.streams.each { stream ->

                        def end_stream = cur_stream
                        if (stream.is_msd) {
                            end_stream += stream.winfiles.size() - 1
                        }

                        if (project.configuration.user_configuration.models.cmp.streams.size() > 1) {
                            for (i in 2..project.configuration.user_configuration.models.global.nb_emitting_states+1)
                                {
                                cmp_untying_file.append("UT {*.state[$i].stream[$cur_stream-$end_stream]}\n")
                            }
                        }  else {
                            for (i in 2..project.configuration.user_configuration.models.global.nb_emitting_states+1)
                                {
                                cmp_untying_file.append("UT {*.state[$i]\n}")
                            }

                        }
                        cur_stream = end_stream + 1

                    }

                    //  2. untying
                    doLast {
                        project.configuration.hts_wrapper.HHEdOnMMF(project.hhed_script_dir + "/untying_cmp.hhed",
                                                                    project.full_list_filename,
                                                                    project.cmp_model_dir + "/clustered.mmf." + local_cur_clus_it,
                                                                    project.cmp_model_dir + "/fullcontext.mmf"  + "." + (local_cur_clus_it+1),
                                                                    [])


                        (new File("$project.buildDir/achievedstages/untyingCMP" + cur_clus_it)).text = "ok"
                    }

                }


                project.task("untyingDUR" + local_cur_clus_it, dependsOn: "trainClusteredModels" + local_cur_clus_it) {

                    inputs.files "$project.buildDir/achievedstages/trainClusteredModels" + cur_clus_it
                    outputs.files "$project.buildDir/achievedstages/untyingDUR" + cur_clus_it

                    //  1. Generate hhed script file
                    def dur_untying_file = new File(project.hhed_script_dir + "/untying_dur.hhed")
                    dur_untying_file.write("// untie parameter sharing structure\n")
                    dur_untying_file.append("UT {*.state[2]}\n")

                    //  2. untying
                    doLast {
                        project.configuration.hts_wrapper.HHEdOnMMF(project.hhed_script_dir + "/untying_dur.hhed",
                                                                    project.full_list_filename,
                                                                    project.dur_model_dir + "/clustered.mmf." + local_cur_clus_it,
                                                                    project.dur_model_dir + "/fullcontext.mmf"  + "." + (local_cur_clus_it+1),
                                                                    [])

                        (new File("$project.buildDir/achievedstages/untyingDUR" + cur_clus_it)).text = "ok"
                    }
                }

                project.task("trainFullContext" + (local_cur_clus_it+1), dependsOn:["untyingCMP" + local_cur_clus_it, "untyingDUR" + local_cur_clus_it]) {

                    inputs.files "$project.buildDir/achievedstages/untyingCMP" + local_cur_clus_it,
                        "$project.buildDir/achievedstages/untyingDUR" + local_cur_clus_it
                    outputs.files "$project.buildDir/achievedstages/trainFullContext" + (local_cur_clus_it+1)

                    doLast {

                        for (i in 1..project.configuration.user_configuration.settings.training.nIte) {

                            project.configuration.hts_wrapper.HERest(project.train_scp,
                                                                     project.full_list_filename,
                                                                     project.full_mlf_filename,
                                                                     project.cmp_model_dir + "/fullcontext.mmf." + (local_cur_clus_it+1),
                                                                     project.dur_model_dir + "/fullcontext.mmf." + (local_cur_clus_it+1),
                                                                     project.cmp_model_dir, project.dur_model_dir,
                                                                     ["-C", project.non_variance_config_filename,
                                                                      "-s", project.cmp_model_dir + "/stats." + (local_cur_clus_it+1),
                                                                      "-w", 0.0])

                            (new File("$project.buildDir/achievedstages/trainFullContext" + (local_cur_clus_it+1))).text = "ok"
                        }
                    }
                }
            }
        }
    }
}
