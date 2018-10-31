package de.dfki.mary.htsvoicebuilding.export

import groovy.json.* // To load the JSON configuration file

import java.nio.file.Files
import java.nio.file.Paths
import org.apache.commons.io.FileUtils

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


class ExportRAW {
    public static void addTasks(Project project) {
        def last_cluster = project.configuration.user_configuration.settings.training.nb_clustering - 1
        def export_dir = new File("$project.buildDir/raw")
        export_dir.mkdirs()

        // FIXME: problem here !
        project.task("exportRAWTrees") {
            dependsOn project.property("trainClusteredModels_${last_cluster}")

            doLast {
                def tree_dir = new File("$export_dir/trees")
                tree_dir.mkdirs()

                project.configuration.user_configuration.models.cmp.streams.each { stream ->
                    project.copy {
                        from "${project.configurationVoiceBuilding.tree_dir}/${stream.name}.${last_cluster}.inf"
                        into tree_dir
                        rename { file -> "${stream.name}.inf"}
                    }
                }

                project.copy {
                    from "${project.configurationVoiceBuilding.tree_dir}/dur.${last_cluster}.inf"
                    into tree_dir
                    rename { file -> "dur.inf"}
                }
            }
        }

        project.task("exportRAWHMMModels") {
            dependsOn project.property("trainClusteredModels_${last_cluster}")

            doLast {
                def model_dir = new File("$export_dir/models")
                model_dir.mkdirs()

                // Copy model files
                project.copy {
                    from project.property("trainClusteredModels_${last_cluster}").trained_cmp_file
                    into model_dir
                    rename { file -> "re_clustered_cmp.mmf" }
                }

                project.copy {
                    from project.property("trainClusteredModels_${last_cluster}").trained_dur_file
                    into model_dir
                    rename { file -> "re_clustered_dur.mmf" }
                }
            }
        }

        project.task("exportRAWLists") {
            dependsOn project.property("trainClusteredModels_${last_cluster}")

            doLast {
                project.copy {
                    from project.configurationVoiceBuilding.full_list_filename
                    into "$export_dir"
                    rename { file -> "full.list" }
                }
            }
        }

        project.task("exportRAWGV") {
            dependsOn project.property("trainGVClustered")

            doLast {
                def gv_dir = new File("$export_dir/gv")
                gv_dir.mkdirs()

                // Trees
                project.configuration.user_configuration.models.cmp.streams.each { stream ->
                    project.copy {
                        from "${project.configurationVoiceBuilding.gv_dir}/${stream.name}.inf"
                        into gv_dir
                        rename { file -> "${stream.name}.inf"}
                    }
                }

                // Model
                project.copy {
                    from project.trainGVClustered.trained_model_file
                    into gv_dir
                    rename { file -> "clustered.mmf"}
                }

                // List
                project.copy {
                    from project.generateGVListFile.list_file
                    into gv_dir
                    rename { file -> "gv.list"}
                }
            }
        }

        project.task("exportRAWWindows") {
            doLast {

                (new File("$export_dir/win")).mkdirs()
                project.configuration.user_configuration.models.cmp.streams.each { stream ->
                    stream.winfiles.each { win_file ->
                        project.copy {
                            from win_file
                            into "$export_dir/win/"
                            rename { file -> win_file.getName() }
                        }
                    }
                }
            }
        }


        project.task("exportRAWDNNModels") {
            dependsOn project.property("trainDNN")

            doLast {
                FileUtils.copyDirectory(project.trainDNN.model_dir.getAsFile().get(),
                                        new File("$export_dir/DNN/models"));

                FileUtils.copyDirectory(new File("$project.buildDir/dnn/var"),
                                        new File("$export_dir/DNN/var"));

                Files.copy(Paths.get(project.configuration.user_configuration.settings.dnn.qconf),
                           Paths.get("$export_dir/DNN/qconf.conf"));
            }
        }


        project.task("exportRAWConfiguration") {
            dependsOn "configurationVoiceBuilding"
            doLast {
                // Adapt configuration file and expose it
                def export_configuration = project.configuration.user_configuration
                export_configuration.remove("data")

                export_configuration.models.cmp.streams.each { stream ->
                    def win = []
                    stream.winfiles.each { win_file ->
                        win.add(win_file.getName())
                    }
                    stream.winfiles = win
                }

                // TODO: convert all files references to string

                (new File("$export_dir/config.json")).text = new JsonBuilder(export_configuration).toPrettyString()
            }
        }

        project.task("exportRAW") {
            dependsOn project.exportRAWTrees
            dependsOn project.exportRAWHMMModels
            dependsOn project.exportRAWWindows
            dependsOn project.exportRAWLists
            dependsOn project.exportRAWConfiguration

            if (project.configuration.user_configuration.gv.use) {
                dependsOn project.exportRAWGV
            }

            if ((project.configuration.user_configuration.settings.training.kind) &&
                (project.configuration.user_configuration.settings.training.kind.equals("dnn")))  {
                dependsOn project.exportRAWDNNModels
            }
        }
    }
}
