package cc.codechecker.api.runtime;

import com.google.common.base.Optional;

import org.apache.commons.exec.*;

import java.io.*;
import java.util.Map;

public class ShellExecutorHelper {

    final Map<String, String> environment;

    public ShellExecutorHelper(Map<String, String> environment) {
        this.environment = environment;
    }

    /**
     * Executes the given bash script with a one sec time limit and returns it's first output line
     * from STDOUT.
     *
     * @param script Bash script, executed with bash -c "{script}"
     * @return first line of the script's output
     */
    public Optional<String> quickReturnFirstLine(String script) {
        Executor ec = build(1000);
        try {
            OneLineReader olr = new OneLineReader();
            ec.setStreamHandler(new PumpStreamHandler(olr));
            ec.execute(buildScriptCommandLine(script), environment);
            return olr.getLine();
        } catch (IOException e) {
            return Optional.absent();
        }
    }

    /**
     * Returns the full output.
     */
    public Optional<String> quickReturnOutput(String script) {
        Executor ec = build(1000);
        try {
            AllLineReader olr = new AllLineReader();
            ec.setStreamHandler(new PumpStreamHandler(olr));
            ec.execute(buildScriptCommandLine(script), environment);
            return Optional.of(olr.getOutput());
        } catch (IOException e) {
            return Optional.absent();
        }
    }

    public Optional<String> waitReturnOutput(String script) {
        Executor ec = build();
        try {
            AllLineReader olr = new AllLineReader();
            ec.setStreamHandler(new PumpStreamHandler(olr));
            ec.execute(buildScriptCommandLine(script), environment);
            return Optional.of(olr.getOutput());
        } catch (IOException e) {
            return Optional.absent();
        }
    }

    /**
     * Executes the given bash script with a one sec time limit and returns based on it's exit
     * status.
     *
     * @param script Bash script, executed with bash -c "{script}"
     * @return true if successful
     */
    public boolean quickAndSuccessfull(String script) {
        Executor ec = build(1000);
        try {
            ec.execute(buildScriptCommandLine(script), environment);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public Executable getServerObject(String cmd) {
        Executor ec = build();
        PidObject pidObject = new PidObject();
        ec.setWatchdog(new ExecuteWatchdog(ExecuteWatchdog.INFINITE_TIMEOUT));
        ServerLoggerReader olr = new ServerLoggerReader(pidObject);
        ec.setStreamHandler(new PumpStreamHandler(olr));
        return new Executable(ec, buildScriptCommandLine("echo \"PID> $BASHPID\" ; " + cmd),
                pidObject);
    }

    private CommandLine buildScriptCommandLine(String script) {
        CommandLine cl = new CommandLine("/bin/bash");
        cl.addArgument("-c");
        cl.addArgument(script, false);
        return cl;
    }

    private Executor build() {
        return build(Optional.<String>absent());
    }

    private Executor build(long timeOutInMilliSec) {
        return build(Optional.<String>absent(), timeOutInMilliSec);
    }

    private Executor build(Optional<String> workingDirectory) {
        return build(workingDirectory, ExecuteWatchdog.INFINITE_TIMEOUT);
    }

    private Executor build(Optional<String> workingDirectory, long timeoutInMilliSec) {
        ExecuteWatchdog ew = new ExecuteWatchdog(timeoutInMilliSec);
        DefaultExecutor executor = new DefaultExecutor();
        //executor.setWorkingDirectory(new File(workingDirectory.or(".")));
        executor.setWatchdog(ew);
        return executor;
    }

    class PidObject {
        int pid;
    }

    public class Executable {
        private final Executor executor;
        private final CommandLine cmdLine;
        private final PidObject pidObject;

        public Executable(Executor executor, CommandLine cmdLine, PidObject pidObject) {
            this.executor = executor;
            this.cmdLine = cmdLine;
            this.pidObject = pidObject;
        }

        public void kill() {
            System.out.println("Killing PID " + this.pidObject.pid);
            if (pidObject.pid > 1000) {
                // Slightly less AWFUL BASH MAGIC, which gets the pids of the pidObject process and
                //     all its descendant processes and kills them.
                // The pidObject process should always be the main CodeChecker process this plugin
                //     starts.
                waitReturnOutput("list_descendants () { "
                        + "local children=$(ps -o pid= --ppid \"$1\"); "
                        + "for pid in $children; do "
                        + "list_descendants \"$pid\"; done; "
                        + "echo \"$children\"; "
                        + "}; "
                        + "kill $(list_descendants " + pidObject.pid + ")");
            }
        }

        public void start() {
            try {
                executor.execute(cmdLine, environment);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    class OneLineReader extends LogOutputStream {

        public Optional<String> line = Optional.absent();

        public Optional<String> getLine() {
            return line;
        }

        @Override
        protected void processLine(String s, int i) {
            if (!line.isPresent()) line = Optional.of(s);
        }
    }

    class AllLineReader extends LogOutputStream {

        public StringBuffer buffer = new StringBuffer();

        public String getOutput() {
            return buffer.toString();
        }

        @Override
        protected void processLine(String s, int i) {
            buffer.append(s + "\n");
        }
    }

    class ServerLoggerReader extends LogOutputStream {

        boolean firstLine = true;
        private PidObject pidObject;

        public ServerLoggerReader(PidObject pidObject) {
            this.pidObject = pidObject;
        }

        @Override
        protected void processLine(String s, int i) {

            if (firstLine) {
                // this is the pid!
                String[] a = s.split(" ");
                if (pidObject != null) {
                    pidObject.pid = Integer.parseInt(a[a.length - 1]);
                    System.out.println("Server PID: " + pidObject.pid);
                }
                firstLine = false;
            }

            // TODO: log to file!
            System.out.println("SERVER> " + s);
        }
    }
}
