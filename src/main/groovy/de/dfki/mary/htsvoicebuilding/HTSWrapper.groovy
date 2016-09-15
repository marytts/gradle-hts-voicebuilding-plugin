package de.dfki.mary.htsvoicebuilding

class HTSWrapper {

    def global_options
    def beams
    def train_config_filename
    def training_wf
    def nb_proc
    def herest_pl_script
    def map_command = [:]

    public HTSWrapper(def beams, def train_config_filename, def training_wf,
                      def nb_proc, def herest_pl_script, def debug)
    {
        global_options = ["-A", "-C", train_config_filename,  "-D", "-T", "1"]
        this.beams = beams
        this.training_wf = training_wf
        this.nb_proc = nb_proc
        this.herest_pl_script = herest_pl_script
        init()
    }

    private void init()
    {
        // Commands
        map_command["hcompv"]    = ["HCompV"]    + global_options + ["-m"]
        map_command["hinit"]     = ["HInit"]     + global_options + ["-m", "1", "-u", "tmvw", "-w", training_wf]
        map_command["hrest"]     = ["HRest"]     + global_options + ["-m", "1", "-u", "tmvw", "-w", training_wf]
        map_command["hhed"]      = ["HHEd"]      + global_options + ["-p", "-i"]
        map_command["hsmmalign"] = ["HSMMAlign"] + global_options + ["-w", "1.0", "-t"] + beams

        // Parallize commands
        if (nb_proc > 1) {
            map_command["herest"] = ["perl", herest_pl_script] + [nb_proc] + global_options +
                ["-m", "1", "-u", "tmvwdmv", "-w", training_wf, "-t"] +
                beams
        } else {
            map_command["herest"] = ["HERest"] + global_options +
                ["-m", "1", "-u", "tmvwdmv", "-w", training_wf, "-t"] +
                beams
        }
    }

    private int executeOnShell(String command) {
        return executeOnShell(command, new File(System.properties.'user.dir'))
    }

    private int executeOnShell(String command, File workingDir) {
        def process = new ProcessBuilder(addShellPrefix(command))
                                .directory(workingDir)
                                .redirectErrorStream(true)
                                .start()

        process.inputStream.eachLine {println it}
        process.waitFor();

        return process.exitValue()
    }

    private String[] addShellPrefix(String command) {
        String[] commandArray = new String[3]
        commandArray[0] = "sh"
        commandArray[1] = "-c"
        commandArray[2] = command
        return commandArray
    }

    public void HCompV(def scp_filename, def proto_filename, def average_filename, def output_dir) {
        def cur_command = map_command["hcompv"]
        cur_command +=  ["-S", scp_filename,
                         "-M", output_dir,
                         "-o", average_filename,
                         proto_filename]

        executeOnShell(cur_command.join(" "))
    }

    public void HInit(def phone, def scp_filename, def proto_filename, def init_mmf_filename,
                      def mlf_filename, def output_dir)
    {
        def cur_command = map_command["hinit"]
        cur_command +=  [
            "-S", scp_filename,
            "-H", init_mmf_filename,
            "-M", output_dir,
            "-I", mlf_filename,
            "-l", "$phone",
            "-o", "$phone",
            proto_filename]

        executeOnShell(cur_command.join(" "))
    }

    public void HRest(def phone, def scp_filename, def cmp_init_dir, def init_mmf_filename,
                      def mlf_filename, def cmp_output_dir, def dur_output_dir)
    {
        def cur_command = map_command["hrest"]
        cur_command +=  [
            "-S", scp_filename,
            "-H", init_mmf_filename,
            "-M", cmp_output_dir,
            "-I", mlf_filename,
            "-l", "$phone",
            "-g", "$dur_output_dir/$phone",
            "$cmp_init_dir/$phone"]

        executeOnShell(cur_command.join(" "))
    }


    public void HHEdOnDir(def hhed_script_filename, def list_filename, def input_dir, def output_file)
    {
        def cur_command = map_command["hhed"]
        cur_command +=  [
            "-d", input_dir,
            "-w", output_file,
            hhed_script_filename,
            list_filename
        ]

        executeOnShell(cur_command.join(" "))
    }

    public void HHEdOnMMF(def hhed_script_filename, def list_filename, def input_mmf, def output_file, def params)
    {
        def cur_command = map_command["hhed"]
        cur_command +=  [
            "-H", input_mmf,
            "-w", output_file
        ]
        cur_command += params

        cur_command += [
            hhed_script_filename,
            list_filename
        ]

        executeOnShell(cur_command.join(" "))
    }

    public void HERest(def scp_filename, def list_filename, def mlf_filename,
                       def cmp_input_filename, def dur_input_filename,
                       def cmp_output_dir, def dur_output_dir,
                       def params)
    {
        def cur_command = map_command["herest"]
        cur_command +=  [
            "-S", scp_filename,
            "-I", mlf_filename,
            "-H", cmp_input_filename,
            "-M", cmp_output_dir,
            "-N", dur_input_filename,
            "-R", dur_output_dir
        ]

        cur_command += params

        cur_command += [list_filename, list_filename]

        executeOnShell(cur_command.join(" "))
    }


    public void HERestGV(def scp_filename, def list_filename, def mlf_filename,
                         def cmp_input_filename, def cmp_output_dir, def params)
    {
        def cur_command = map_command["herest"]
        cur_command +=  [
            "-S", scp_filename,
            "-I", mlf_filename,
            "-H", cmp_input_filename,
            "-M", cmp_output_dir
        ]

        cur_command += params

        cur_command += [list_filename]

        executeOnShell(cur_command.join(" "))
    }


    public void HSMMAlign(def scp_filename, def list_filename, def mlf_filename,
                          def cmp_input_filename, def dur_input_filename, def output_dir)
    {
        def cur_command = map_command["hsmmalign"]
        cur_command +=  [
            "-S", scp_filename,
            "-I", mlf_filename,
            "-H", cmp_input_filename,
            "-N", dur_input_filename,
            "-m", output_dir,
            list_filename, list_filename]

        executeOnShell(cur_command.join(" "))
    }

}
