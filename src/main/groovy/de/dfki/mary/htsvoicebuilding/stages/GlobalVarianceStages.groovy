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


import de.dfki.mary.htsvoicebuilding.stages.task.gv.*
import de.dfki.mary.htsvoicebuilding.stages.task.config.GenerateListTask
import de.dfki.mary.htsvoicebuilding.stages.task.config.GenerateMLFTask
import de.dfki.mary.htsvoicebuilding.stages.task.config.GenerateSCPTask



class GlobalVarianceStages
{
    public static void addTasks(Project project)
    {
        project.task('generateGVProtoFile', type: GenerateGVProtoTask) {
            description "Generate the global variance prototype file"

            // Inputs
            template_file = project.file("${project.configurationVoiceBuilding.template_dir}/protogv")

            // Outputs
            proto_file = project.file("${project.configurationVoiceBuilding.gv_dir}/proto")
        }

        project.task('generateStateForceAlignment', type: GenerateStateForceAlignmentTask) {
            description "Generate the state force alignment"

            def last_clust = project.configuration.user_configuration.settings.training.nb_clustering - 1

            // Global Files
            scp_file = project.generateSCPFile.scp_file
            list_file = project.generateFullList.list_file
            mlf_file = project.generateFullMLF.mlf_file

            // Model files
            model_cmp_file = project.property("trainClusteredModels${last_clust}").trained_cmp_file
            model_dur_file = project.property("trainClusteredModels${last_clust}").trained_dur_file

            // State Alignment
            alignment_dir = project.file("${project.configurationVoiceBuilding.gv_fal_dir}/state")
        }

        project.task('forceAlignment', type: GeneratePhoneForceAlignmentTask)  {
            description "Generate the phone force alignment"

            // Inputs
            scp_file = project.generateSCPFile.scp_file
            state_alignment_dir = project.generateStateForceAlignment.alignment_dir

            // outputs
            phone_alignment_dir = project.file("${project.configurationVoiceBuilding.gv_fal_dir}/phone")
        }

        project.task('GVCoefficientsExtraction', type: ExtractGVCoefficientsTask) {
            description "Extract global variance coefficients"

            // Inputs
            scp_file = project.generateSCPFile.scp_file // FIXME: just used to get the filename by the way...
            if (project.configuration.user_configuration.gv.disable_force_alignment) {
                lab_dir = project.file(project.configuration.user_configuration.gv.label_dir)
            } else {
                lab_dir = project.forceAlignment.phone_alignment_dir
            }

            // Outputs
            cmp_dir = project.configurationVoiceBuilding.gv_data_dir
        }

        project.task("generateGVLabFiles", type: GenerateGVLabFilesTask) {
            description "Generate the global variance label files"

            // Inputs
            scp_file = project.generateSCPFile.scp_file // FIXME: just used to get the filename by the way...
            full_lab_dir = project.file(project.configuration.user_configuration.data.full_lab_dir)

            // Outputs
            gv_lab_dir = project.configurationVoiceBuilding.gv_lab_dir
        }

        project.task("generateGVSCPFile", type: GenerateSCPTask) {
            description "Generate the GV SCP file listing the global variance data files"

            dependsOn 'GVCoefficientsExtraction' // FIXME

            // Inputs
            list_basenames = project.file(project.configuration.user_configuration.data.list_files)
            data_dir = project.configurationVoiceBuilding.gv_data_dir

            // Otputs
            scp_file = project.file("${project.configurationVoiceBuilding.gv_scp_dir}/train.scp")
        }

        project.task('generateGVListFile', type: GenerateListTask) {
            description "Generate the global variance label list file"

            // Inputs
            lab_dir = project.generateGVLabFiles.gv_lab_dir
            list_basenames = project.file(project.configuration.user_configuration.data.list_files)

            // Outputs
            list_file = project.file("${project.configurationVoiceBuilding.list_dir}/gv.list")

            // // FIXME: how to integrate this?
            // if (project.configuration.user_configuration.gv.cdgv) {
            //     list_file.write(model_set.join("\n"))
            // } else {
            //     list_file.write("gv")
            // }
        }

        project.task('generateGVMLFFile', type: GenerateMLFTask) {
            description "Generate the global variance master label file"

            // Inputs
            lab_dir = project.generateGVLabFiles.gv_lab_dir

            // Outputs
            mlf_file = project.file("${project.configurationVoiceBuilding.mlf_dir}/gv.mlf")
        }

        /**************************************************************************************************************
         ** Train base models
         **************************************************************************************************************/
        project.task('generateGVAverage', type: GenerateGVAverageTask)  {
            description "Generate the global variance average model"

            // Inputs
            scp_file = project.generateGVSCPFile.scp_file
            proto_file = project.generateGVProtoFile.proto_file

            // Outputs
            average_file = project.file("${project.configurationVoiceBuilding.gv_dir}/average.mmf")
            vfloor_file = project.file("${project.configurationVoiceBuilding.gv_dir}/vFloors")
        }

        project.task('generateGVFullContext', type: GenerateGVFullContextTask) {
            description "Generate the global variance full context model"

            // Inputs
            list_file = project.generateGVListFile.list_file
            average_file = project.generateGVAverage.average_file
            vfloor_file = project.generateGVAverage.vfloor_file

            // Outputs
            model_file = project.file("${project.configurationVoiceBuilding.gv_dir}/init/fullcontext.mmf")
        }

        project.task('trainGVFullcontext', type: TrainGVModelsTask) {
            description "Train the global variance full context model"

            // Inputs
            scp_file = project.generateGVSCPFile.scp_file
            list_file = project.generateGVListFile.list_file
            mlf_file = project.generateGVMLFFile.mlf_file
            init_model_file = project.generateGVFullContext.model_file

            // Outputs
            trained_model_file = project.file("${project.configurationVoiceBuilding.gv_dir}/trained/fullcontext.mmf")
            stats_file = project.file("${project.configurationVoiceBuilding.gv_dir}/stats")

            // Parameters
            options = ["-C", project.configurationVoiceBuilding.non_variance_config_filename, "-w", 0]
        }


        /**************************************************************************************************************
         ** Clustering
         **************************************************************************************************************/
        project.task("clusteringGV", type: ClusteringGVTask) {
            description "Generate the clustered global variance model files (1 file per stream)"

            // Inputs
            script_template_file = project.file("${project.configurationVoiceBuilding.template_dir}/cxc.hed");
            list_file = project.generateGVListFile.list_file
            question_file = new File (project.configuration.user_configuration.data.question_file_gv)
            stats_file = project.trainGVFullcontext.stats_file
            fullcontext_model_file = project.trainGVFullcontext.trained_model_file

            // Outputs
            def m_files = []
            project.configuration.user_configuration.models.cmp.streams.each { stream ->
                def f = project.file("$project.configurationVoiceBuilding.gv_dir/init/clustered.mmf.${stream.name}")
                m_files.add(f)
            }
            clustered_model_files.setFrom(m_files)
        }

        project.task("joinClusteredGV", type: JoinClusteredGVTask) {
            description "Join the independently trained global variance clustered files"

            // FIXME: why do I nee this?
            dependsOn "clusteringGV"

            // Inputs
            list_file = project.generateGVListFile.list_file
            script_file = project.file("${project.configurationVoiceBuilding.hhed_script_dir}/join.gv.hed")
            clustered_cmp_files = project.property("clusteringGV").clustered_model_files

            // outputs
            clustered_model_file = project.file("$project.configurationVoiceBuilding.gv_dir/init/clustered.mmf")
        }

        project.task("trainGVClustered", type: TrainGVModelsTask) {
            description "Train the global variance clustered file"

            // Inputs
            scp_file = project.generateGVSCPFile.scp_file
            list_file = project.generateGVListFile.list_file
            mlf_file = project.generateGVMLFFile.mlf_file
            init_model_file = project.joinClusteredGV.clustered_model_file

            // Outputs
            stats_file = project.file("${project.configurationVoiceBuilding.gv_dir}/stats_clustered")
            trained_model_file = project.file("${project.configurationVoiceBuilding.gv_dir}/trained/clustered.mmf")
        }

        /*
        // TODO: finish it !
        project.task("averageGV2clustered", dependsOn:"generateGVAverage")
        {
            // FIXME: add inputs.files
            inputs.files project.configurationVoiceBuilding.gv_dir + "/average.mmf", project.configurationVoiceBuilding.gv_dir + "/vFloors"
            outputs.files project.configurationVoiceBuilding.gv_dir + "/clustered.mmf"
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

                (project.file("${project.configurationVoiceBuilding.gv_dir}/average.mmf")).eachLine { line ->
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
                (project.file("${project.configurationVoiceBuilding.gv_dir}/vFloors")).eachLine { line ->
                    head += line + "\n"
                }


                // Generate clustered model from average data
                def clustered_mmf = project.file("${project.configurationVoiceBuilding.gv_dir}/clustered.mmf")
                clustered_mmf.write(head)
                s = 1
                project.configuration.user_configuration.models.cmp.streams.each { stream ->
                    clustered_mmf.append("~p \"gv_" + stream.name + "_1\"\n")
                    clustered_mmf.append("<STREAM> $s\n")
                    clustered_mmf.append(pdf[s-1])
                    s += 1
                }

                clustered_mmf.append("~h \"gv\"\n")
                clustered_mmf.append(mid)
                s = 1
                project.configuration.user_configuration.models.cmp.streams.each { stream ->
                    clustered_mmf.append("<STREAM> $s\n")
                    clustered_mmf.append("~p \"gv_" + stream.name + "_1\"\n")
                    s += 1
                }
                clustered_mmf.append(tail)
            }
        }
         */
    }
}
