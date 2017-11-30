package com.github.mcollovati.vaadin;

import javax.servlet.annotation.WebServlet;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import com.vaadin.annotations.Push;
import com.vaadin.annotations.Theme;
import com.vaadin.annotations.VaadinServletConfiguration;
import com.vaadin.data.Binder;
import com.vaadin.data.converter.StringToLongConverter;
import com.vaadin.server.VaadinRequest;
import com.vaadin.server.VaadinServlet;
import com.vaadin.ui.Button;
import com.vaadin.ui.Label;
import com.vaadin.ui.Notification;
import com.vaadin.ui.TextField;
import com.vaadin.ui.UI;
import com.vaadin.ui.VerticalLayout;
import com.vaadin.ui.themes.ValoTheme;

/**
 * This UI is the application entry point. A UI may either represent a browser window
 * (or tab) or some part of an HTML page where a Vaadin application is embedded.
 * <p>
 * The UI is initialized using {@link #init(VaadinRequest)}. This method is intended to be
 * overridden to add component to the user interface and initialize non-component functionality.
 */
@Theme(ValoTheme.THEME_NAME)
@Push
public class MyUI extends UI {

    private ScheduledExecutorService executorService;
    private VerticalLayout layout;
    private Label progress = new Label();

    @Override
    protected void init(VaadinRequest vaadinRequest) {
        layout = new VerticalLayout();

        AtomicLong timeoutValue = new AtomicLong(500);
        TextField timeoutField = new TextField("Timeout in millis");
        Binder<AtomicLong> binder = new Binder<>();
        binder
            .forField(timeoutField)
            .withConverter(new StringToLongConverter("Must be a numeric value"))
            .bind(AtomicLong::get, AtomicLong::set);
        binder.setBean(timeoutValue);


        Button button = new Button("Click Me");
        button.addClickListener(e -> limitExecutionTime(timeoutValue.get(), this::generateReport));

        layout.addComponents(timeoutField, button, progress);

        setContent(layout);
    }

    @Override
    public void attach() {
        super.attach();
        executorService = Executors.newScheduledThreadPool(5);
    }

    @Override
    public void detach() {
        super.detach();
        executorService.shutdown();
    }

    private void generateReport(UIAccessor uiAccessor) {
        uiAccessor.doWithUI(ui -> ((MyUI) ui).progress.setValue("Start"));
        System.out.println(">>> Start");
        int times = 0;
        for (int x = 0; x < 100000000; x++) {
            ThreadLocalRandom.current().nextInt();
            if (x % 1000000 == 0) {
                String log = ">>> Done " + ++times;
                System.out.println(log);
                uiAccessor.doWithUI(ui -> ((MyUI) ui).progress.setValue(log));
            }
        }
        uiAccessor.doWithUI(ui -> ((MyUI) ui).progress.setValue("Task completed, maybe should not reach here"));
        System.out.println(">>> Should not reach here");
    }


    public CompletableFuture<?> limitExecutionTime(long timeoutMillis, Consumer<UIAccessor> lambda) {
        UI currentUI = UI.getCurrent();
        UIAccessor accessor = new UIAccessor(currentUI);
        CompletableFuture<?> job = new CompletableFuture<>();
        CompletableFuture.runAsync(() -> {
            Future<?> f = executorService.submit(() -> lambda.accept(accessor));
            executorService.schedule(() -> f.cancel(true), timeoutMillis, TimeUnit.MILLISECONDS);
            try {
                f.get();
                job.complete(null);
            } catch (InterruptedException | ExecutionException | CancellationException e) {
                job.completeExceptionally(e);
            }
        });
        return job.whenComplete((unused, throwable) -> currentUI.access(() -> {
                if (throwable != null) {
                    Notification.show("Execution took beyond the maximum allowed time.", Notification.Type.ERROR_MESSAGE);
                } else {
                    // So someting with UI when computation has ended
                    Notification.show("Job completed.", Notification.Type.HUMANIZED_MESSAGE);
                }
            })
        );
    }

    static class UIAccessor {
        private final UI ui;

        public UIAccessor(UI ui) {
            this.ui = ui;
        }

        void doWithUI(Consumer<UI> consumer) {
            System.out.println("Accessor thread: " + Thread.currentThread().toString());
            if (Thread.currentThread().isInterrupted()) {
                System.out.println("STOP me!!!");
                throw new RuntimeException("Thread has been interrupted, stopping job");
            }
            ui.access(() -> consumer.accept(ui));
        }
    }


    @WebServlet(urlPatterns = "/*", name = "MyUIServlet", asyncSupported = true)
    @VaadinServletConfiguration(ui = MyUI.class, productionMode = false)
    public static class MyUIServlet extends VaadinServlet {
    }
}
