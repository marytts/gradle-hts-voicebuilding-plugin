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
import de.dfki.mary.htsvoicebuilding.stages.task.monophone.*
import de.dfki.mary.htsvoicebuilding.stages.task.TrainModelsTask

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

        project.task('generateMonophoneMMF', type:GenerateMonophoneModelTask)
        {
            // Scripts part
            script_template_cmp_file = new File(project.template_dir, 'lvf.hed')
            script_cmp_file = new File(project.hhed_script_dir, "lvf.cmp.hed")
            script_dur_file = new File(project.hhed_script_dir, "lvf.dur.hed")

            // Used vfloor
            vfloor_cmp_file = project.initModels.vfloor_cmp_file
            vfloor_dur_file = project.initModels.vfloor_dur_file

            // Input hrest results
            cmp_hrest_dir = project.initialiseMonophoneModels.cmp_hrest_dir
            dur_hrest_dir = project.initialiseMonophoneModels.dur_hrest_dir

            // Model filename
            cmp_mmf_file = new File(project.cmp_model_dir + "/monophone/init/monophone.mmf")
            dur_mmf_file = new File(project.dur_model_dir + "/monophone/init/monophone.mmf")

            // List file
            list_file = project.generateMonophoneList.list_file
        }

        project.task('trainMonophoneMMF', type: TrainModelsTask)
        {
            use_daem = project.configuration.user_configuration.settings.daem.use

            scp_file = project.generateSCPFile.scp_file
            list_file = project.generateMonophoneList.list_file
            mlf_file = project.generateMonoMLF.mlf_file

            init_cmp_file = project.generateMonophoneMMF.cmp_mmf_file
            init_dur_file = project.generateMonophoneMMF.dur_mmf_file

            trained_cmp_file = new File(project.cmp_model_dir + "/monophone/trained/monophone.mmf")
            trained_dur_file = new File(project.dur_model_dir + "/monophone/trained/monophone.mmf")
        }
    }
}
