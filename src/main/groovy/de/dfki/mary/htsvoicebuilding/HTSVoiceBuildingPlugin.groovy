package de.dfki.mary.htsvoicebuilding

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

import de.dfki.mary.htsvoicebuilding.export.*
import de.dfki.mary.htsvoicebuilding.stages.*

import groovy.json.JsonBuilder
import groovy.json.JsonSlurper
import groovy.xml.*

class HTSVoicebuildingPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.plugins.apply JavaPlugin
        project.plugins.apply MavenPlugin

        project.sourceCompatibility = JavaVersion.VERSION_1_7


        project.ext {

            // Scp
            train_scp = "$project.buildDir/train.scp"

            // List directories
            list_dir = "$project.buildDir/lists"
            mono_list_filename = "$project.list_dir/mono.list"
            full_list_filename = "$project.list_dir/full.list"

            // MLF Directory
            mlf_dir = "$project.buildDir/mlf"
            mono_mlf_filename = "$project.mlf_dir/mono.mlf"
            full_mlf_filename = "$project.mlf_dir/full.mlf"

            // Tree filenames
            tree_dir = "$project.buildDir/trees"

            // Script directories
            hhed_script_dir = "$project.buildDir/edfiles/"

            // Configuration project
            config_dir = "$project.buildDir/configs"
            train_config_filename = "$project.config_dir/trn.cfg"
            non_variance_config_filename = "$project.config_dir/nvf.cfg"

            // Output
            voicesDirectory = "$project.buildDir/voices"
            genDirectory = "$project.buildDir/gen"

            // Model directories
            global_model_dir = "$project.buildDir/models"
            cmp_model_dir = "$project.buildDir/models/cmp"
            dur_model_dir = "$project.buildDir/models/dur"
            proto_dir = "$project.buildDir/models/proto"

            // Global variance
            gv_dir        = "$project.buildDir/gv/models"
            gv_data_dir   = "$project.buildDir/gv/data"
            gv_fal_dir    = "$project.buildDir/gv/fal"
            gv_lab_dir    = "$project.buildDir/gv/labels"
            gv_scp_dir    = "$project.buildDir/gv/scp" // FIXME

            trained_files = new HashMap()

            // HTS wrapper
            utils_dir = "$project.buildDir/tmp/utils"
            template_dir = "$project.buildDir/tmp/templates"

        }

        project.task("configuration") {

            dependsOn "configurationVoiceBuilding"

            // Adapt pathes
            DataFileFinder.project_path = new File(getClass().protectionDomain.codeSource.location.path).parent
            if (project.configurationVoiceBuilding.user_configuration.data.project_dir) {
                DataFileFinder.project_path = project.configurationVoiceBuilding.user_configuration.data.project_dir
            }

            println(DataFileFinder.project_path)

            // User configuration
            ext.user_configuration = project.configurationVoiceBuilding.hasProperty("user_configuration") ? project.configurationVoiceBuilding.user_configuration : null
            ext.nb_proc = project.configurationVoiceBuilding.hasProperty("nb_proc") ? project.configurationVoiceBuilding.nb_proc : 1

            def debug = false
            if (project.configurationVoiceBuilding.user_configuration.settings.debug) {
                debug = true
            }
            def beams = project.configurationVoiceBuilding.user_configuration.settings.training.beam.split() as List
            ext.hts_wrapper = new HTSWrapper(beams, "$project.train_config_filename",
                                         project.configurationVoiceBuilding.user_configuration.settings.training.wf, project.configuration.nb_proc,
                                         "$project.buildDir/tmp/utils/HERest.pl", debug)



        }

        addPrepareEnvironmentTask(project)

        project.afterEvaluate {
            // Add the tasks
            InitialisationStages.addTasks(project)
            MonophoneStages.addTasks(project)
            ContextStages.addTasks(project)
            GlobalVarianceStages.addTasks(project)
            DNNStages.addTasks(project)
            addExportingTasks(project)
            addRunTask(project)
        }
    }


    /****************************************************************************************
     ** Export stages
     ****************************************************************************************/
    private void addPrepareEnvironmentTask(Project project)
    {
        project.task('prepareEnvironment')
        {
            dependsOn "configuration"

            // Create model and trees directories
            new File(project.proto_dir).mkdirs()
            new File(project.cmp_model_dir).mkdirs()
            new File(project.dur_model_dir).mkdirs()
            new File(project.tree_dir).mkdirs()

            // MLF & list
            (new File(project.list_dir)).mkdirs()
            (new File(project.mlf_dir)).mkdirs()

            // Scripts and configuration
            new File(project.hhed_script_dir).mkdirs()
            new File(project.config_dir).mkdirs()

            // Specific initialisation directory
            new File(project.cmp_model_dir + "/HRest").mkdirs()
            new File(project.dur_model_dir + "/HRest").mkdirs()
            if (!project.configuration.user_configuration.settings.daem.use) {
                new File(project.cmp_model_dir + "/HInit").mkdirs()
                new File(project.dur_model_dir + "/Hinit").mkdirs()
            }

            // GV
            new File(project.gv_dir).mkdirs()
            new File(project.gv_data_dir).mkdirs()
            new File(project.gv_fal_dir).mkdirs()
            new File(project.gv_lab_dir).mkdirs()
            new File(project.gv_scp_dir).mkdirs()

            (new File(project.template_dir)).mkdirs()
            def templates = ['Config.java',
                             'ConfigTest.java',
                             'LoadVoiceIT.java',
                             'average_dur.mmf',
                             'cxc.hed',
                             'database.config',
                             'lvf.hed',
                             'mmf2htsengine.hed',
                             'mocc.cfg',
                             'nvf.cfg',
                             'proto',
                             'protogv',
                             'train.cfg',
                             'train_dnn.cfg',
                             'voice-straight-hsmm.config',
                             'vfloordur',
                            ].collect {
                project.file "$project.template_dir/$it"
            }


            templates.each { outputFile ->
                outputFile.withOutputStream { stream ->
                    stream << getClass().getResourceAsStream("/de/dfki/mary/htsvoicebuilding/templates/$outputFile.name")
                }
            }

            // Util. scripts
            (new File(project.utils_dir)).mkdirs()
            def utils = ['HERest.pl',
                         'addhtkheader.pl',
                         'makefeature.pl',
                         'DNNDataIO.py',
                         'DNNDefine.py',
                         'DNNTraining.py'
                        ].collect {
                project.file "$project.utils_dir/$it"
            }

            utils.each { outputFile ->
                outputFile.withOutputStream { stream ->
                    stream << getClass().getResourceAsStream("/de/dfki/mary/htsvoicebuilding/utils/$outputFile.name")
                }
            }
        }
    }

    /****************************************************************************************
     ** Export stages
     ****************************************************************************************/
    private void addExportingTasks(Project project) {
        project.task('training', dependsOn: "trainClusteredModels" + (project.configuration.user_configuration.settings.training.nb_clustering - 1))
        {
            if (project.configuration.user_configuration.gv.use) {
                dependsOn.add("trainGV")
            }

            if ((project.configuration.user_configuration.settings.training.kind) &&
                (project.configuration.user_configuration.settings.training.kind.equals("dnn")))
            {
                dependsOn.add("trainDNN")
            }
        }

        project.task('exportPreparation', dependsOn:'training')
        {
            doLast {
                // Models
                project.trained_files.put("mmf_cmp", project.cmp_model_dir + "/clustered.mmf." + (project.configuration.user_configuration.settings.training.nb_clustering - 1))
                project.trained_files.put("mmf_dur", project.dur_model_dir + "/clustered.mmf." + (project.configuration.user_configuration.settings.training.nb_clustering - 1))

                if (project.configuration.user_configuration.gv.use) {
                    project.trained_files.put("mmf_gv", project.gv_dir + "/clustered.mmf")
                }


                // Tree files
                project.configuration.user_configuration.models.cmp.streams.each { stream ->
                    project.trained_files.put(stream.name + "_tree",
                                              project.tree_dir + "/" + stream.name  + "." + (project.configuration.user_configuration.settings.training.nb_clustering - 1) + ".inf")

                    if (project.configuration.user_configuration.gv.use) {
                        project.trained_files.put(stream.name + "_tree_gv",
                                                  project.gv_dir + "/" + stream.name  + ".inf")
                    }
                }

                project.trained_files.put("dur_tree",
                                          project.tree_dir + "/dur." + (project.configuration.user_configuration.settings.training.nb_clustering - 1) + ".inf")

                // Lists
                project.trained_files.put("full_list",
                                          project.full_list_filename)

                if (project.configuration.user_configuration.gv.use) {
                    project.trained_files.put("list_gv",
                                              project.list_dir + "/gv.list")
                }
            }
        }

        project.task('exportRAW', dependsOn: 'exportPreparation')
        {
            doLast {
                Raw.export(project)
            }
        }


        /******************************
         ** MaryTTS
         ******************************/
        project.task('prepareMary') {

            doLast {
            }
        }

        project.compileJava.dependsOn project.prepareMary

        project.task('exportMaryTTS', dependsOn:project.jar)  {
        }
    }


    /****************************************************************************************
     ** Run (Main) task
     ****************************************************************************************/
    private void addRunTask(Project project)
    {
        project.task('run')
        {
            // RAW
            if (project.configuration.user_configuration.output.raw) {
                dependsOn "exportRAW"
            }


            // RAW
            if (project.configuration.user_configuration.output.marytts) {
                dependsOn "exportMaryTTS"
            }
        }
    }
}
