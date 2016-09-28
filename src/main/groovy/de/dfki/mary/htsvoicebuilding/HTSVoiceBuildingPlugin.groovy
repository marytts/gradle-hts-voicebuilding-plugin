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

        // Load configuration
        def slurper = new JsonSlurper()
        def config_file =  new File(System.getProperty("configuration"))
        def config = slurper.parseText( config_file.text )

        // Adapt pathes
        DataFileFinder.project_path = new File(getClass().protectionDomain.codeSource.location.path).parent
        if (config.data.project_dir) {
            DataFileFinder.project_path = config.data.project_dir
        }

        def beams = config.settings.training.beam.split() as List
        def nb_proc_local = 1
        if (project.gradle.startParameter.getMaxWorkerCount() != 0) {
            nb_proc_local = Runtime.getRuntime().availableProcessors(); // By default the number of core
            if (config.settings.nb_proc) {
                if (config.settings.nb_proc > nb_proc_local) {
                    throw Exception("You will overload your machine, preventing stop !")
                }

                nb_proc_local = config.settings.nb_proc
            }
        }

        project.ext {
            maryttsVersion = '5.1.2'
            maryttsSrcDir = "$project.buildDir/marytts/src/main/java"
            maryttsResourcesDir = "$project.buildDir/marytts/src/main/resources"
            // maryttsTestSrcDir = "$project.buildDir/marytts/"
            new ConfigSlurper().parse(project.rootProject.file('voice.groovy').text).each { key, value ->
                set key, value
            }
            voice.nameCamelCase = voice.name?.split(/[^_A-Za-z0-9]/).collect { it.capitalize() }.join()
            voice.locale = voice.locale?.country ? new Locale(voice.locale.language, voice.locale.country) : new Locale(voice.locale.language)
            voice.localeXml = [voice.locale.language, voice.locale.country].join('-')
            voice.maryLocaleXml = voice.locale.language.equalsIgnoreCase(voice.locale.country) ? voice.locale.language : voice.localeXml
            basenames = project.rootProject.subprojects.findAll { it.parent.name == 'data' }.collect { it.name }

            export_dir = "${project.buildDir}/marytts/src/main/resources/marytts/voice/${voice.name}"

            // User configuration
            user_configuration = config;

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

            // Nb processes
            nb_proc = nb_proc_local

            // HTS wrapper
            utils_dir = "$project.buildDir/tmp/utils"
            def debug = false
            if (config.settings.debug) {
                debug = true
            }
            hts_wrapper = new HTSWrapper(beams, "$project.train_config_filename",
                                         config.settings.training.wf, nb_proc_local,
                                         "$project.buildDir/tmp/utils/HERest.pl", debug)

            template_dir = "$project.buildDir/tmp/templates"

        }

        project.status = project.version.endsWith('SNAPSHOT') ? 'integration' : 'release'

        project.repositories {
            jcenter()
            maven {
                url 'http://oss.jfrog.org/artifactory/repo'
            }
        }

        project.sourceSets {
            main {
                java {
                    srcDir project.maryttsSrcDir
                }
                resources {
                    srcDir project.maryttsResourcesDir
                }
            }
            // test {
            //     java {
            //         srcDir project.generatedTestSrcDir
            //     }
            // }
        }


        project.jar.manifest {
            attributes('Created-By': "${System.properties['java.version']} (${System.properties['java.vendor']})",
            'Built-By': System.properties['user.name'],
            'Built-With': "gradle-${project.gradle.gradleVersion}, groovy-${GroovySystem.version}")
        }


        project.afterEvaluate {
            project.dependencies {
                compile "de.dfki.mary:marytts-lang-$project.voice.locale.language:$project.maryttsVersion"
                testCompile "junit:junit:4.11"
            }

            // Add the tasks
            addPrepareEnvironmentTask(project)
            InitialisationStages.addTasks(project)
            MonophoneStages.addTasks(project)
            ContextStages.addTasks(project)
            GlobalVarianceStages.addTasks(project)
            addExportingTasks(project)
            addRunTask(project)
        }
    }


    /****************************************************************************************
     ** Export stages
     ****************************************************************************************/
    private void addPrepareEnvironmentTask(Project project)
    {
        project.task('prepareEnvironment', dependsOn: project.rootProject.tasks.prepareData)
        {

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
            if (!project.user_configuration.settings.daem.use) {
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
                         'addhtkheader.pl'
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
        project.task('training', dependsOn: "trainClusteredModels" + (project.user_configuration.settings.training.nb_clustering - 1))
        {
            if (project.user_configuration.gv.use) {
                dependsOn.add("trainGV")
            }
        }

        project.task('exportPreparation', dependsOn:'training')
        {
            doLast {
                // Models
                project.trained_files.put("mmf_cmp", project.cmp_model_dir + "/clustered.mmf." + (project.user_configuration.settings.training.nb_clustering - 1))
                project.trained_files.put("mmf_dur", project.dur_model_dir + "/clustered.mmf." + (project.user_configuration.settings.training.nb_clustering - 1))

                if (project.user_configuration.gv.use) {
                    project.trained_files.put("mmf_gv", project.gv_dir + "/clustered.mmf")
                }


                // Tree files
                project.user_configuration.models.cmp.streams.each { stream ->
                    project.trained_files.put(stream.name + "_tree",
                                              project.tree_dir + "/" + stream.name  + "." + (project.user_configuration.settings.training.nb_clustering - 1) + ".inf")

                    if (project.user_configuration.gv.use) {
                        project.trained_files.put(stream.name + "_tree_gv",
                                                  project.gv_dir + "/" + stream.name  + ".inf")
                    }
                }

                project.trained_files.put("dur_tree",
                                          project.tree_dir + "/dur." + (project.user_configuration.settings.training.nb_clustering - 1) + ".inf")

                // Lists
                project.trained_files.put("full_list",
                                          project.full_list_filename)

                if (project.user_configuration.gv.use) {
                    project.trained_files.put("list_gv",
                                              project.list_dir + "/gv.list")
                }
            }
        }

        project.task('exportRAW', dependsOn: 'exportPreparation')
        {
            outputs.files project.fileTree("$project.export_dir")
            doLast {
                Raw.export(project)
            }
        }


        /******************************
         ** MaryTTS
         ******************************/
        project.task('prepareMary') {
            def serviceLoaderFile = project.file("$project.maryttsResourcesDir/META-INF/services/marytts.config.MaryConfig")

            // FIXME: Inputs & Outputs
            outputs.files serviceLoaderFile
            outputs.files project.fileTree("$project.export_dir")

            doLast {
                MaryTTS.export(project)

                // Generate Service loader :
                serviceLoaderFile.parentFile.mkdirs()
                serviceLoaderFile.text = "marytts.voice.${project.voice.name}.Config"
                project.processResources {project.fileTree("$project.export_dir")}
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
            if (project.user_configuration.output.raw) {
                dependsOn "exportRAW"
            }


            // RAW
            if (project.user_configuration.output.marytts) {
                dependsOn "exportMaryTTS"
            }
        }
    }
}
