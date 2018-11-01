package de.dfki.mary.htsvoicebuilding.stages

import java.util.Hashtable;

// Grade imports
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
import de.dfki.mary.htsvoicebuilding.stages.task.config.GenerateMLFTask
import de.dfki.mary.htsvoicebuilding.stages.task.config.GenerateSCPTask
import de.dfki.mary.htsvoicebuilding.stages.task.config.GenerateListTask
import de.dfki.mary.htsvoicebuilding.stages.task.config.GenerateTrainingConfigurationTask
import de.dfki.mary.htsvoicebuilding.stages.task.config.GenerateMOCCConfigurationFileTask
import de.dfki.mary.htsvoicebuilding.stages.task.init.*


class InitialisationStages {

    /****************************************************************************************
     **
     ****************************************************************************************/
    public static void addTasks(Project project)
    {
        project.task('generateSCPFile', type: GenerateSCPTask) {
            description "Generate the SCP file which contains the list of files used to train the models"

            list_basenames = project.file(project.configuration.user_configuration.data.list_files)
            data_dir = project.configurationVoiceBuilding.cmp_dir // FIXME: more generic
            scp_file = project.configurationVoiceBuilding.train_scp
        }

        project.task("generateMonoMLF", type: GenerateMLFTask) {
            description "Generate the Master Label File for Monophone training"

            mlf_file = project.file(project.configurationVoiceBuilding.mono_mlf_filename)
            lab_dir = project.file(project.configuration.user_configuration.data.mono_lab_dir)
        }

        project.task('generateMonophoneList', type: GenerateListTask) {
            description "Generate the list of monophone labels"

            lab_dir = project.file(project.configuration.user_configuration.data.mono_lab_dir)
            list_basenames = project.file(project.configuration.user_configuration.data.list_files)
            list_file = project.file(project.configurationVoiceBuilding.mono_list_filename)
        }

        project.task('generatePrototype', type: GeneratePrototypeTask) {
            description "Generate the HMM prototype file"

            template_file = project.file("${project.configurationVoiceBuilding.template_dir}/proto")
            prototype_file = project.file("${project.configurationVoiceBuilding.proto_dir}/proto")
        }

        project.task("generateTrainingConfigurationFile", type: GenerateTrainingConfigurationTask) {
            description "Generate the training configuration file"

            template_file = project.file("${project.configurationVoiceBuilding.template_dir}/train.cfg")
            configuration_file = project.file("${project.configurationVoiceBuilding.config_dir}/train.cfg")
        }

        project.task("generateNVCConfigurationFile") {
            description "Generate the Non Variance Configuration file"

            doLast {
                // nvf.cfg
                project.copy {
                    from project.configurationVoiceBuilding.template_dir
                    into project.configurationVoiceBuilding.config_dir

                    include "nvf.cfg"
                    rename { file -> project.configurationVoiceBuilding.non_variance_config_filename.name }
                }
            }
        }

        project.task("generateMOCCCMPConfigurationFiles", type:GenerateMOCCConfigurationFileTask) {
            description "Generate the MOCC configuration files for CMP part"

            Hashtable<File, Float> val = new Hashtable<File, Float>();

            // Fill values
            def m_files = []
            project.configuration.user_configuration.models.cmp.streams.each { stream ->
                def f = project.file("${project.configurationVoiceBuilding.config_dir}/${stream.name}.cfg")
                m_files.add(f)
                mocc_values.put(f, stream.mocc);
            }
            mocc_files.setFrom(m_files)

            // FIXME: should be dependency
            template_file = project.file("${project.configurationVoiceBuilding.template_dir}/mocc.cfg")
        }

        project.task("generateMOCCDURConfigurationFile", type:GenerateMOCCConfigurationFileTask) {
            description "Generate the MOCC configuration file for duration part"

            def f = project.file("${project.configurationVoiceBuilding.config_dir}/dur.cfg")
            mocc_files.setFrom([f])

            // Generate value hash
            mocc_values.put(f, project.configuration.user_configuration.models.dur.mocc);

            // FIXME: should be dependency
            template_file = project.file("${project.configurationVoiceBuilding.template_dir}/mocc.cfg")
        }

        project.task('initModels', type: InitModelsTask) {
            description "Compute initialisation model information"

            // FIXME: refactor
            dependsOn "generateTrainingConfigurationFile"
            dependsOn "generateNVCConfigurationFile"

            // FIXME: should be dependency
            vfloor_dur_template_file = project.file("$project.configurationVoiceBuilding.template_dir/vfloordur")
            average_dur_template_file = project.file("$project.configurationVoiceBuilding.template_dir/average_dur.mmf")

            // Input prototype/scp
            scp_file = project.generateSCPFile.scp_file
            prototype_file = project.generatePrototype.prototype_file

            // CMP initialisation files
            vfloor_cmp_file = project.file("${project.configurationVoiceBuilding.cmp_model_dir}/vFloors")
            init_cmp_file = project.file("${project.configurationVoiceBuilding.cmp_model_dir}/init.mmf")
            average_cmp_file = project.file("${project.configurationVoiceBuilding.cmp_model_dir}/average.mmf")

            // Duration initialisation files
            vfloor_dur_file = project.file("${project.configurationVoiceBuilding.dur_model_dir}/vFloors")
            average_dur_file = project.file("${project.configurationVoiceBuilding.dur_model_dir}/average.mmf")
        }
    }
}
