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


// Task imports
import de.dfki.mary.htsvoicebuilding.stages.task.context.*
import de.dfki.mary.htsvoicebuilding.stages.task.config.GenerateMLFTask
import de.dfki.mary.htsvoicebuilding.stages.task.config.GenerateListTask
import de.dfki.mary.htsvoicebuilding.stages.task.TrainModelsTask

class ContextStages
{
    public static void addTasks(Project project)
    {
        project.task('generateFullMLF', type: GenerateMLFTask) {
            description "Generate the master label file for the full context labels"

            // Inputs
            lab_dir = project.file(project.configuration.user_configuration.data.full_lab_dir)

            // Outputs
            mlf_file = project.file(project.configurationVoiceBuilding.full_mlf_filename)
        }

        project.task('generateFullList', type: GenerateListTask) {
            description "Generate the full context label list file"

            // Inputs
            lab_dir = project.file(project.configuration.user_configuration.data.full_lab_dir)
            list_basenames = project.file(project.configuration.user_configuration.data.list_files)

            // Outputs
            list_file = project.file(project.configurationVoiceBuilding.full_list_filename)
        }

        project.task('generateFullcontextFromMonophone', type: GenerateFullModelsTask) {
            description "Generate the fullcontext MMF files from the trained monophone MMF"

            // Lists
            mono_list_file = project.generateMonophoneList.list_file
            full_list_file = project.generateFullList.list_file

            // Mono
            mono_model_cmp_file = project.trainMonophoneMMF.trained_cmp_file
            mono_model_dur_file = project.trainMonophoneMMF.trained_dur_file

            // Full
            full_model_cmp_file = project.file("${project.configurationVoiceBuilding.cmp_model_dir}/fullcontext_0/init/fullcontext.mmf")
            full_model_dur_file = project.file("${project.configurationVoiceBuilding.dur_model_dir}/fullcontext_0/init/fullcontext.mmf")

            // Scripts
            m2f_script_file = project.file("${project.configurationVoiceBuilding.hhed_script_dir}/m2f.hed")
        }

        project.task('trainFullContext0', type: TrainModelsTask) {
            description "Train the full context model files (iteration 0)"

            // Disable daem as it is only for monophone
            use_daem = false

            // Configuration file
            scp_file = project.generateSCPFile.scp_file
            list_file = project.generateFullList.list_file
            mlf_file = project.generateFullMLF.mlf_file

            // Input models
            init_cmp_file = project.generateFullcontextFromMonophone.full_model_cmp_file
            init_dur_file = project.generateFullcontextFromMonophone.full_model_dur_file

            // Output models
            trained_cmp_file = project.file("${project.configurationVoiceBuilding.cmp_model_dir}/fullcontext_0/trained/fullcontext.mmf")
            trained_dur_file = project.file("${project.configurationVoiceBuilding.dur_model_dir}/fullcontext_0/trained/fullcontext.mmf")

            // Parameters
            options = ["-C", project.configurationVoiceBuilding.non_variance_config_filename, "-s", "${project.configurationVoiceBuilding.cmp_model_dir}/stats.0", "-w", 0.0]
        }


        project.configuration.user_configuration.settings.training.nb_clustering.times { cur_clus_it ->

            project.task("clusteringCMP" + cur_clus_it, type: ClusteringCMPTask) {
                description "Cluster the CMP part independently (iteration $cur_clus_it)"

                // FIXME: refactor
                dependsOn "generateMOCCCMPConfigurationFiles"

                // Save the it. number
                local_cur_clus_it = cur_clus_it;

                // Inputs
                script_template_file = project.file("${project.configurationVoiceBuilding.template_dir}/cxc.hed");
                list_file = project.generateFullList.list_file
                stats_cmp_file = project.file("${project.configurationVoiceBuilding.cmp_model_dir}/stats.${cur_clus_it}")
                fullcontext_model_file = project.property("trainFullContext${cur_clus_it}").trained_cmp_file;
                question_file = project.file(project.configuration.user_configuration.data.question_file);

                // Outputs
                def m_files = []
                project.configuration.user_configuration.models.cmp.streams.each { stream ->
                    def f = project.file("$project.configurationVoiceBuilding.cmp_model_dir/fullcontext_$cur_clus_it/init/clustered.mmf.${stream.name}.$local_cur_clus_it")
                    m_files.add(f)
                }
                clustered_model_files.setFrom(m_files)
            }

            project.task("joinClusteredCMP" + cur_clus_it, type: JoinClusteredCMPTask) {
                description "Join the clustered CMP mmf model files (iteration $cur_clus_it)"

                // FIXME: why do I nee this?
                dependsOn "clusteringCMP${cur_clus_it}"

                // Inputs
                list_file = project.generateFullList.list_file
                script_file = project.file("${project.configurationVoiceBuilding.hhed_script_dir}/join.hed.${cur_clus_it}")
                clustered_cmp_files = project.property("clusteringCMP${cur_clus_it}").clustered_model_files

                // outputs
                clustered_model_file = project.file("$project.configurationVoiceBuilding.cmp_model_dir/fullcontext_$cur_clus_it/init/clustered.mmf")

                // Parameters
                local_cur_clus_it = cur_clus_it;
            }

            project.task("clusteringDUR" + cur_clus_it, type: ClusteringDURTask) {
                description "Cluster the duration part (iteration $cur_clus_it)"

                // FIXME: refactor
                dependsOn "generateMOCCDURConfigurationFile"

                // local_cur_clus_it = cur_clus_it;
                list_file = project.generateFullList.list_file
                script_template_file =  project.file("${project.configurationVoiceBuilding.template_dir}/cxc.hed");

                // Question file
                question_file = project.file(project.configuration.user_configuration.data.question_file)
                script_file = project.file("${project.configurationVoiceBuilding.hhed_script_dir}/cxc_dur.hed.${cur_clus_it}")
                tree_file = project.file("${project.configurationVoiceBuilding.tree_dir}/dur.${cur_clus_it}.inf")
                config_file = project.file("${project.configurationVoiceBuilding.config_dir}/dur.cfg")

                // Copy stats
                stats_cmp_file = project.file("${project.configurationVoiceBuilding.cmp_model_dir}/stats.$cur_clus_it")
                stats_dur_file = project.file("${project.configurationVoiceBuilding.dur_model_dir}/stats.$cur_clus_it")

                // Model paths
                fullcontext_model_file = project.property("trainFullContext${cur_clus_it}").trained_dur_file
                clustered_model_file = project.file("${project.configurationVoiceBuilding.dur_model_dir}/fullcontext_${cur_clus_it}/init/clustered.mmf")
            }

            project.task("trainClusteredModels" + cur_clus_it, type: TrainModelsTask) {
                description "Train the clustered models (iteration $cur_clus_it)"

                // Configuration files
                scp_file = project.generateSCPFile.scp_file
                list_file = project.generateFullList.list_file
                mlf_file = project.generateFullMLF.mlf_file

                // Init. model files
                init_cmp_file = project.property("joinClusteredCMP" + cur_clus_it).clustered_model_file
                init_dur_file = project.property("clusteringDUR" + cur_clus_it).clustered_model_file

                // Trained model files
                trained_cmp_file = project.file("${project.configurationVoiceBuilding.cmp_model_dir}/fullcontext_${cur_clus_it}/trained/clustered.mmf")
                trained_dur_file = project.file("${project.configurationVoiceBuilding.dur_model_dir}/fullcontext_${cur_clus_it}/trained/clustered.mmf")
            }


            if (cur_clus_it  < project.configuration.user_configuration.settings.training.nb_clustering) {

                project.task("untyingCMP" + cur_clus_it, type: UntyingCMPTask) {

                    description "Untie the CMP clustered models (iteration $cur_clus_it)"

                    // Inputs
                    list_file = project.generateFullList.list_file
                    input_model_file = project.property("trainClusteredModels${cur_clus_it}").trained_cmp_file

                    // Ooutputs
                    untying_script_file = project.file("${project.configurationVoiceBuilding.hhed_script_dir}/untying_cmp.hhed")
                    output_model_file = project.file("${project.configurationVoiceBuilding.cmp_model_dir}/fullcontext_${cur_clus_it+1}/init/fullcontext.mmf")
                }


                project.task("untyingDUR" + cur_clus_it, type:UntyingDURTask) {
                    description "Untie the duration clustered model (iteration $cur_clus_it)"

                    // Inputs
                    list_file = project.generateFullList.list_file
                    input_model_file = project.property("trainClusteredModels${cur_clus_it}").trained_dur_file

                    // Outputs
                    untying_script_file = project.file("${project.configurationVoiceBuilding.hhed_script_dir}/untying_dur.hhed")
                    output_model_file = project.file("${project.configurationVoiceBuilding.dur_model_dir}/fullcontext_${cur_clus_it+1}/init/fullcontext.mmf")
                }

                project.task("trainFullContext${cur_clus_it+1}", type: TrainModelsTask) {
                    description "Train the fullcontext models (iteration ${cur_clus_it+1})"

                    // Configuration files
                    scp_file = project.generateSCPFile.scp_file
                    list_file = project.generateFullList.list_file
                    mlf_file = project.generateFullMLF.mlf_file

                    // Input model files
                    init_cmp_file = project.property("untyingCMP${cur_clus_it}").output_model_file
                    init_dur_file = project.property("untyingDUR${cur_clus_it}").output_model_file

                    // Output model files
                    trained_cmp_file = project.file("${project.configurationVoiceBuilding.cmp_model_dir}/fullcontext_${(cur_clus_it+1)}/trained/fullcontext.mmf")
                    trained_dur_file = project.file("${project.configurationVoiceBuilding.dur_model_dir}/fullcontext_${(cur_clus_it+1)}/trained/fullcontext.mmf")

                    // Parameters
                    options = ["-C", project.configurationVoiceBuilding.non_variance_config_filename.toString(),
                               "-s", "${project.configurationVoiceBuilding.cmp_model_dir}/stats.${cur_clus_it+1}",
                               "-w", 0.0]
                }
            }
        }
    }
}
