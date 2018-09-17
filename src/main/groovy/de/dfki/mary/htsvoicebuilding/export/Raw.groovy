package de.dfki.mary.htsvoicebuilding.export

import groovy.json.* // To load the JSON configuration file
import java.nio.file.Files
import java.nio.file.Paths
import org.apache.commons.io.FileUtils;

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

        // Copy windows
        (new File("$export_dir/raw/win")).mkdirs()
        user_configuration.models.cmp.streams.each { stream ->
            stream.winfiles.each { winfilename ->
                def winfile = new File(DataFileFinder.getFilePath(winfilename))
                Files.copy(winfile.toPath(), Paths.get("$export_dir/raw/win/" + winfile.getName()))
            }
        }

        // Copy DNN part
        if ((user_configuration.settings.training.kind) &&
            (user_configuration.settings.training.kind.equals("dnn")))
        {
            FileUtils.copyDirectory(new File("$project.buildDir/DNN/models"),
                                    new File("$export_dir/raw/DNN/models"));

            FileUtils.copyDirectory(new File("$project.buildDir/DNN/var"),
                                    new File("$export_dir/raw/DNN/var"));

            Files.copy(Paths.get(user_configuration.settings.dnn.qconf),
                       Paths.get("$export_dir/raw/DNN/qconf.conf"));
        }

        // Finally copy file
        (new File("$export_dir/raw/config.json")).text = new JsonBuilder(user_configuration).toPrettyString()
    }
}
