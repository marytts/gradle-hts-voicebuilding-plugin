package de.dfki.mary.htsvoicebuilding.export

import groovy.json.* // To load the JSON configuration file

class Raw {
    def static export(project) {

        def user_configuration = project.configuration.user_configuration
        def export_dir = project.buildDir
        def trained_files = project.trained_files

        // Define directories
        def model_dir = "$export_dir/raw/models"
        def tree_dir = "$export_dir/raw/trees"
        (new File(model_dir)).mkdirs()
        (new File(tree_dir)).mkdirs()

        // Copy model files
        project.copy {
            from project.property("trainFullContext${user_configuration.settings.training.nb_clustering - 1}").trained_cmp_file
            into model_dir
            rename { file -> "re_clustered_cmp.mmf" }
        }

        project.copy {
            from project.property("trainFullContext${user_configuration.settings.training.nb_clustering - 1}").trained_dur_file
            into model_dir
            rename { file -> "re_clustered_dur.mmf" }
        }

        // Copy tree files
        user_configuration.models.cmp.streams.each { stream ->
            project.copy {
                from trained_files.get(stream.name + "_tree")
                into tree_dir
                rename { file -> "${stream.name}.inf"}
            }
        }

        project.copy {
            from trained_files.get("dur_tree")
            into tree_dir
            rename { file -> "dur.inf"}
        }

        // Copy list
        project.copy {
            from project.generateFullList.list_file
            into "$export_dir/raw"
            rename { file -> "full.list" }
        }


        // Copy gv informations
        if (user_configuration.gv.use) {
            def gv_dir = "$export_dir/raw/gv"
            (new File(gv_dir)).mkdirs()

            // Trees
            user_configuration.models.cmp.streams.each { stream ->
                project.copy {
                    from trained_files.get(stream.name + "_tree_gv")
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

        // Copy windows
        (new File("$export_dir/raw/win")).mkdirs()
        user_configuration.models.cmp.streams.each { stream ->
            stream.winfiles.each { winfilename ->
                project.copy {
                    from winfilename
                    into "$export_dir/raw/win/"
                    rename { file -> (new File(winfilename)).getName() }
                }
            }
        }

        // Copy DNN part (TODO)
        if ((user_configuration.settings.training.kind) &&
            (user_configuration.settings.training.kind.equals("dnn")))
        {
            // FileUtils.copyDirectory(new File("$project.buildDir/DNN/models"),
            //                         new File("$export_dir/raw/DNN/models"));

            // FileUtils.copyDirectory(new File("$project.buildDir/DNN/var"),
            //                         new File("$export_dir/raw/DNN/var"));

            // Files.copy(Paths.get(user_configuration.settings.dnn.qconf),
            //            Paths.get("$export_dir/raw/DNN/qconf.conf"));
        }

        // Finally copy file
        (new File("$export_dir/raw/config.json")).text = new JsonBuilder(user_configuration).toPrettyString()
    }
}
