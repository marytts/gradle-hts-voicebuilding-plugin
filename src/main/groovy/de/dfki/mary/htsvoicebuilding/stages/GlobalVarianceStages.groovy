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
            dependsOn "configurationVoiceBuilding"

            proto_file = new File(project.gv_dir, "proto")
            template_file = new File(project.template_dir, 'protogv')
        }

        project.task('generateStateForceAlignment', type: GenerateStateForceAlignmentTask) {
            def last_clust = project.configuration.user_configuration.settings.training.nb_clustering - 1

            // Global Files
            scp_file = project.generateSCPFile.scp_file
            list_file = project.generateFullList.list_file
            mlf_file = project.generateFullMLF.mlf_file

            // Model files
            model_cmp_file = project.property("trainClusteredModels${last_clust}").trained_cmp_file
            model_dur_file = project.property("trainClusteredModels${last_clust}").trained_dur_file

            // State Alignment
            alignment_dir = new File(project.gv_fal_dir + "/state")
        }

        project.task('forceAlignment', type: GeneratePhoneForceAlignmentTask)  {
            scp_file = project.generateSCPFile.scp_file
            state_alignment_dir = project.generateStateForceAlignment.alignment_dir
            phone_alignment_dir = new File(project.gv_fal_dir + "/phone")
        }

        project.task('GVCoefficientsExtraction', type: ExtractGVCoefficientsTask) {
            dependsOn "configurationVoiceBuilding"

            if (project.configuration.user_configuration.gv.disable_force_alignment) {
                lab_dir = new File(project.configuration.user_configuration.gv.label_dir)
            } else {
                lab_dir = project.forceAlignment.phone_alignment_dir
            }

            cmp_dir = new File(project.gv_data_dir)
            scp_file = project.generateSCPFile.scp_file // FIXME: just used to get the filename by the way...
        }

        project.task("generateGVLabFiles", type: GenerateGVLabFilesTask) {
            scp_file = project.generateSCPFile.scp_file // FIXME: just used to get the filename by the way...
            full_lab_dir = new File(project.configuration.user_configuration.data.full_lab_dir)
            gv_lab_dir = new File(project.gv_lab_dir)
        }

        project.task("generateGVSCPFile", type: GenerateSCPTask) {
            dependsOn 'GVCoefficientsExtraction' // FIXME
            list_basenames = new File(project.configuration.user_configuration.data.list_files)
            data_dir = new File(project.gv_data_dir)
            scp_file = new File(project.gv_scp_dir + "/train.scp")
        }

        project.task('generateGVListFile', type: GenerateListTask) {
            lab_dir = project.generateGVLabFiles.gv_lab_dir
            list_basenames = new File(project.configuration.user_configuration.data.list_files)
            list_file = new File(project.list_dir + "/gv.list")

            // // FIXME: how to integrate this?
            // if (project.configuration.user_configuration.gv.cdgv) {
            //     list_file.write(model_set.join("\n"))
            // } else {
            //     list_file.write("gv")
            // }
        }

        project.task('generateGVMLFFile', type: GenerateMLFTask) {
            lab_dir = project.generateGVLabFiles.gv_lab_dir
            mlf_file = new File(project.mlf_dir + "/gv.mlf")
        }

        /**************************************************************************************************************
         ** Train base models
         **************************************************************************************************************/
        project.task('generateGVAverage', type: GenerateGVAverageTask)
        {
            scp_file = project.generateGVSCPFile.scp_file
            proto_file = project.generateGVProtoFile.proto_file
            average_file = new File(project.gv_dir, "average.mmf")
            vfloor_file = new File(project.gv_dir, "vFloors")
        }

        project.task('generateGVFullContext', type: GenerateGVFullContextTask) {
            list_file = project.generateGVListFile.list_file
            average_file = project.generateGVAverage.average_file
            vfloor_file = project.generateGVAverage.vfloor_file

            model_file = new File(project.gv_dir + "/init", "fullcontext.mmf")
        }

        project.task('trainGVFullcontext', type: TrainGVModelsTask)
        {
            scp_file = project.generateGVSCPFile.scp_file
            list_file = project.generateGVListFile.list_file
            mlf_file = project.generateGVMLFFile.mlf_file

            init_model_file = project.generateGVFullContext.model_file
            trained_model_file = new File(project.gv_dir + "/trained/fullcontext.mmf")

            options = ["-C", project.non_variance_config_filename, "-s", project.gv_dir + "/stats", "-w", 0]
        }


        /**************************************************************************************************************
         ** Clustering
         **************************************************************************************************************/
        project.task("generateGVClustered", type: GenerateGVClusteredTask) {

            // List files
            list_file = project.generateGVListFile.list_file

            // Tree related files
            question_file = new File (project.configuration.user_configuration.data.question_file_gv)
            script_template_file = new File(project.template_dir, 'cxc.hed');

            // Model files
            fullcontext_model_file = project.trainGVFullcontext.trained_model_file
            clustered_model_file = new File(project.gv_dir + "/init", "clustered.mmf")
        }

        project.task("trainGVClustered", type: TrainGVModelsTask)
        {

            scp_file = project.generateGVSCPFile.scp_file
            list_file = project.generateGVListFile.list_file
            mlf_file = project.generateGVMLFFile.mlf_file

            init_model_file = project.generateGVClustered.clustered_model_file
            trained_model_file = new File(project.gv_dir + "/trained/clustered.mmf")
        }

        /*
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


        project.task("trainGV")
        {
            if (project.configuration.user_configuration.gv.cdgv) {
                dependsOn "trainGVClustered"
            }

            /* FIXME: not supported for now ! */
            // else {
            //     dependsOn "averageGV2clustered"
            // }
        }
    }
}
