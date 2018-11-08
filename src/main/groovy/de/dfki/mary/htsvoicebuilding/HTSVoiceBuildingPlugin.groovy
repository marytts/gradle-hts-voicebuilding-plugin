package de.dfki.mary.htsvoicebuilding

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.MavenPlugin

import de.dfki.mary.htsvoicebuilding.export.*
import de.dfki.mary.htsvoicebuilding.stages.*


class HTSVoicebuildingPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.plugins.apply JavaPlugin
        project.plugins.apply MavenPlugin

        project.sourceCompatibility = JavaVersion.VERSION_1_8

        project.task("configurationVoiceBuilding") {

            // Debug switching
            def debug = false
            if (project.configuration.user_configuration.settings.debug) {
                debug = true
            }

            // Data file
            ext.cmp_dir = project.file("${project.buildDir}/cmp")
            ext.ffo_dir = project.file("${project.buildDir}/ffo")

            // model directories
            ext.train_scp = project.file("$project.buildDir/train.scp")

            // Create model and trees directories
            ext.global_model_dir = project.file("$project.buildDir/models")
            ext.cmp_model_dir = project.file("$project.buildDir/models/cmp")
            ext.dur_model_dir = project.file("$project.buildDir/models/dur")
            ext.proto_dir = project.file("$project.buildDir/models/proto")
            ext.tree_dir = project.file("$project.buildDir/trees")
            ext.proto_dir.mkdirs()
            ext.cmp_model_dir.mkdirs()
            ext.dur_model_dir.mkdirs()
            ext.tree_dir.mkdirs()

            // List directories
            ext.list_dir = project.file("$project.buildDir/lists")
            ext.list_dir.mkdirs()
            ext.mono_list_filename = project.file("$ext.list_dir/mono.list")
            ext.full_list_filename = project.file("$ext.list_dir/full.list")

            // MLF Directory
            ext.mlf_dir = project.file("$project.buildDir/mlf")
            ext.mlf_dir.mkdirs()
            ext.mono_mlf_filename = project.file("$ext.mlf_dir/mono.mlf")
            ext.full_mlf_filename = project.file("$ext.mlf_dir/full.mlf")

            // Script directories
            ext.hhed_script_dir = project.file("$project.buildDir/edfiles/")
            ext.hhed_script_dir.mkdirs()

            // Configuration project
            ext.config_dir = project.file("$project.buildDir/configs")
            ext.config_dir.mkdirs()
            ext.train_config_filename = project.file("$ext.config_dir/train.cfg")
            ext.non_variance_config_filename = project.file("$ext.config_dir/nvf.cfg")

            // Specific initialisation directory
            project.file("${ext.cmp_model_dir}/HRest").mkdirs()
            project.file("${ext.dur_model_dir}/HRest").mkdirs()
            if (!project.configuration.user_configuration.settings.daem.use) {
                project.file("${ext.cmp_model_dir}/HInit").mkdirs()
                project.file("${ext.dur_model_dir}/Hinit").mkdirs()
            }

            // Monophone part
            project.file("${ext.cmp_model_dir}/monophone/init").mkdirs()
            project.file("${ext.dur_model_dir}/monophone/init").mkdirs()
            project.file("${ext.cmp_model_dir}/monophone/trained").mkdirs()
            project.file("${ext.dur_model_dir}/monophone/trained").mkdirs()

            // full context
            for (int i=0; i<project.configuration.user_configuration.settings.training.nb_clustering; i++) {
                project.file("${ext.cmp_model_dir}/fullcontext_$i/init").mkdirs()
                project.file("${ext.dur_model_dir}/fullcontext_$i/init").mkdirs()
                project.file("${ext.cmp_model_dir}/fullcontext_$i/trained").mkdirs()
                project.file("${ext.dur_model_dir}/fullcontext_$i/trained").mkdirs()
            }

            // GV
            if (project.configuration.user_configuration.gv.use) {
                ext.gv_dir        = project.file("$project.buildDir/gv/models")
                ext.gv_data_dir   = project.file("$project.buildDir/gv/data")
                ext.gv_fal_dir    = project.file("$project.buildDir/gv/fal")
                ext.gv_lab_dir    = project.file("$project.buildDir/gv/labels")
                ext.gv_scp_dir    = project.file("$project.buildDir/gv/scp") // FIXME

                project.file("${ext.gv_dir}/init").mkdirs()
                project.file("${ext.gv_dir}/trained").mkdirs()
                ext.gv_data_dir.mkdirs()
                ext.gv_fal_dir.mkdirs()
                ext.gv_lab_dir.mkdirs()
                ext.gv_scp_dir.mkdirs()
            } else {
                ext.gv_dir        = null
                ext.gv_data_dir   = null
                ext.gv_fal_dir    = null
                ext.gv_lab_dir    = null
                ext.gv_scp_dir    = null
            }

            // DNN
            if ((project.configuration.user_configuration.settings.training.kind) &&
                (project.configuration.user_configuration.settings.training.kind.equals("dnn"))) {

                ext.qconf = new File(project.configuration.user_configuration.settings.dnn.qconf)
                ext.train_dnn_scp = project.file("$project.buildDir/train_dnn.scp")
                ext.dnn_dir = project.file("${project.buildDir}/dnn/models")
                ext.alignment_dir = project.file("$project.buildDir/dnn/alignment")
                ext.ffo_dir = project.file("$project.buildDir/ffo")
                ext.ffi_dir = project.file("$project.buildDir/dnn/ffi")
                ext.var_dir = project.file("$project.buildDir/dnn/var")

                ext.dnn_dir.mkdirs()
                ext.ffi_dir.mkdirs()
                ext.var_dir.mkdirs()
            } else {
                ext.train_dnn_scp = null
                ext.dnn_dir = null
                ext.alignment_dir = null
                ext.ffo_dir = null
                ext.ffi_dir = null
                ext.var_dir = null
                ext.qconf = null
            }

            // Template/config
            ext.template_dir = project.file("$project.buildDir/tmp/templates")
            ext.template_dir.mkdirs()
            def templates = [
                // HTS default
                'average_dur.mmf',
                'cxc.hed',
                'lvf.hed',
                'mmf2htsengine.hed',
                'mocc.cfg',
                'nvf.cfg',
                'proto',
                'protogv',
                'synth.cfg',
                'train.cfg',
                'train_dnn.cfg',
                'vfloordur',

                // HTS engine
                "htsvoice",
                "cv_hts_engine.hed",

                // MaryTTS
                'Config.java',
                'ConfigTest.java',
                'LoadVoiceIT.java',
                'voice-straight-hsmm.config',
                'database.config',
            ].collect {
                project.file "$ext.template_dir/$it"
            }


            templates.each { outputFile ->
                outputFile.withOutputStream { stream ->
                    stream << getClass().getResourceAsStream("/de/dfki/mary/htsvoicebuilding/templates/$outputFile.name")
                }
            }

            // Util. scripts
            ext.utils_dir = project.file("$project.buildDir/tmp/utils")
            ext.utils_dir.mkdirs()
            def utils = ['HERest.pl',
                         'addhtkheader.pl',
                         'makefeature.pl',
                         'DNNDataIO.py',
                         'DNNDefine.py',
                         'DNNTraining.py'
            ].collect {
                project.file "$ext.utils_dir/$it"
            }

            utils.each { outputFile ->
                outputFile.withOutputStream { stream ->
                    stream << getClass().getResourceAsStream("/de/dfki/mary/htsvoicebuilding/utils/$outputFile.name")
                }
            }

            // Instanciate HTS wrapper
            HTSWrapper.logger = project.logger
            def beams = project.configuration.user_configuration.settings.training.beam.split() as List
            ext.hts_wrapper = new HTSWrapper(beams, "$ext.train_config_filename",
                                             project.configuration.user_configuration.settings.training.wf,
                                             project.gradle.startParameter.getMaxWorkerCount(),
                                             "$project.buildDir/tmp/utils/HERest.pl", debug)
        }


        // Stages
        InitialisationStages.addTasks(project)
        MonophoneStages.addTasks(project)
        ContextStages.addTasks(project)
        GlobalVarianceStages.addTasks(project)
        DNNStages.addTasks(project)

        // Export
        ExportRAW.addTasks(project)
        ExportHTSEngine.addTasks(project)

        //Training task !
        addTrainTask(project)
    }

    /****************************************************************************************
     ** Run (Main) task
     ****************************************************************************************/
    private void addTrainTask(Project project) {
        project.task('train') {
            // RAW
            if (project.configuration.user_configuration.output.raw) {
                dependsOn "exportRAW"
            }

            // HTS engine
            if (project.configuration.user_configuration.output.hts_engine) {
                dependsOn "exportHTSEngine"
            }

            // // MaryTTS
            // if (project.configuration.user_configuration.output.marytts) {
            //     dependsOn "exportMaryTTS"
            // }
        }
    }
}
