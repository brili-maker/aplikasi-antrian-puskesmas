import javax.swing.SwingWorker;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;

/** Menjalankan pekerjaan database di thread latar (SwingWorker) agar GUI tidak membeku. */
final class Async {
    static <T> void run(Callable<T> job, Consumer<T> onOk, Consumer<Exception> onFail) {
        new SwingWorker<T, Void>() {
            @Override protected T doInBackground() throws Exception { return job.call(); }
            @Override protected void done() {   // berjalan di thread GUI
                try {
                    onOk.accept(get());
                } catch (ExecutionException e) {
                    onFail.accept(e.getCause() instanceof Exception ? (Exception) e.getCause() : e);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }.execute();
    }

    private Async() {}
}
