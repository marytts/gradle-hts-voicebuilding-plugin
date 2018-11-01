package de.dfki.mary.htsvoicebuilding.stages

// Gradle imports
import org.gradle.api.Project

// Task imports
import de.dfki.mary.htsvoicebuilding.stages.task.monophone.*
import de.dfki.mary.htsvoicebuilding.stages.task.TrainModelsTask

class MonophoneStages {

    /****************************************************************************************
     **
     ****************************************************************************************/
    public static void addTasks(Project project)
    {
        project.task('initialiseMonophoneModels', type: InitPhoneModelsTask)  {
            description "Initialise the monophone model files using HInit and HRest"

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
            cmp_hinit_dir = project.file("$project.configurationVoiceBuilding.cmp_model_dir/HInit/")
            cmp_hrest_dir = project.file("$project.configurationVoiceBuilding.cmp_model_dir/HRest/")

            // Output dur files
            dur_hrest_dir = project.file("$project.configurationVoiceBuilding.dur_model_dir/HRest/")
        }

        project.task('generateMonophoneMMF', type:GenerateMonophoneModelTask) {
            description "Generate the monophone MMF file"

            // Scripts part
            script_template_cmp_file = project.file("${project.configurationVoiceBuilding.template_dir}/lvf.hed")
            script_cmp_file = project.file("${project.configurationVoiceBuilding.hhed_script_dir}/lvf.cmp.hed")
            script_dur_file = project.file("${project.configurationVoiceBuilding.hhed_script_dir}/lvf.dur.hed")

            // Used vfloor
            vfloor_cmp_file = project.initModels.vfloor_cmp_file
            vfloor_dur_file = project.initModels.vfloor_dur_file

            // Input hrest results
            cmp_hrest_dir = project.initialiseMonophoneModels.cmp_hrest_dir
            dur_hrest_dir = project.initialiseMonophoneModels.dur_hrest_dir

            // Model filename
            cmp_mmf_file = project.file("${project.configurationVoiceBuilding.cmp_model_dir}/monophone/init/monophone.mmf")
            dur_mmf_file = project.file("${project.configurationVoiceBuilding.dur_model_dir}/monophone/init/monophone.mmf")

            // List file
            list_file = project.generateMonophoneList.list_file
        }

        project.task('trainMonophoneMMF', type: TrainModelsTask) {
            description "EM training of the monophone model"

            use_daem = project.configuration.user_configuration.settings.daem.use

            scp_file = project.generateSCPFile.scp_file
            list_file = project.generateMonophoneList.list_file
            mlf_file = project.generateMonoMLF.mlf_file

            init_cmp_file = project.generateMonophoneMMF.cmp_mmf_file
            init_dur_file = project.generateMonophoneMMF.dur_mmf_file

            trained_cmp_file = project.file("${project.configurationVoiceBuilding.cmp_model_dir}/monophone/trained/monophone.mmf")
            trained_dur_file = project.file("${project.configurationVoiceBuilding.dur_model_dir}/monophone/trained/monophone.mmf")
        }
    }
}
