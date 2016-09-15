package de.dfki.mary.htsvoicebuilding.export

import groovy.json.* // To load the JSON configuration file
import java.nio.file.Files
import java.nio.file.Paths

class Raw {
    def static export(project) {

        def user_configuration = project.user_configuration
        def export_dir = project.buildDir
        def trained_files = project.trained_files

        // Define directories
        def model_dir = "$export_dir/raw/models"
        def tree_dir = "$export_dir/raw/trees"
        (new File(model_dir)).mkdirs()
        (new File(tree_dir)).mkdirs()

        // Copy model files
        Files.copy(Paths.get(trained_files.get("mmf_cmp")),
                   Paths.get(model_dir + "/re_clustered_cmp.mmf"))
        Files.copy(Paths.get(trained_files.get("mmf_dur")),
                   Paths.get(model_dir + "/re_clustered_dur.mmf"))

        // Copy tree files
        user_configuration.models.cmp.streams.each { stream ->
            Files.copy(Paths.get(trained_files.get(stream.name + "_tree")),
                       Paths.get(tree_dir + "/" + stream.name + ".inf"))
        }
        Files.copy(Paths.get(trained_files.get("dur_tree")),
                   Paths.get(tree_dir + "/dur.inf"))

        // Copy list
        Files.copy(Paths.get(trained_files.get("full_list")),
                   Paths.get("$export_dir/raw/full.list"))


        // Copy gv informations
        if (user_configuration.gv.use) {
            def gv_dir = "$export_dir/raw/gv"
            (new File(gv_dir)).mkdirs()

            // Trees
            user_configuration.models.cmp.streams.each { stream ->
                Files.copy(Paths.get(trained_files.get(stream.name + "_tree_gv")),
                           Paths.get(gv_dir + "/" + stream.name + ".inf"))
            }

            // Model
            Files.copy(Paths.get(trained_files.get("mmf_gv")),
                       Paths.get(gv_dir + "/clustered.mmf"))

            // List
            Files.copy(Paths.get(trained_files.get("list_gv")),
                       Paths.get(gv_dir + "/gv.list"))
        }
    }
}
