package com.paulhammant.jbehave.scenarios;

import org.jbehave.core.configuration.Configuration;
import org.jbehave.core.configuration.groovy.GroovyContext;
import org.jbehave.core.configuration.groovy.GroovyResourceFinder;
import org.jbehave.core.failures.FailingUponPendingStep;
import org.jbehave.core.io.CodeLocations;
import org.jbehave.core.io.LoadFromClasspath;
import org.jbehave.core.io.StoryFinder;
import org.jbehave.core.junit.JUnitStories;
import org.jbehave.core.reporters.ConsoleOutput;
import org.jbehave.core.reporters.StoryReporter;
import org.jbehave.core.reporters.StoryReporterBuilder;
import org.jbehave.core.steps.CandidateSteps;
import org.jbehave.core.steps.SilentStepMonitor;
import org.jbehave.core.steps.Steps;
import org.jbehave.core.steps.groovy.GroovyStepsFactory;
import org.jbehave.web.selenium.ContextView;
import org.jbehave.web.selenium.LocalFrameContextView;
import org.jbehave.web.selenium.PerStoriesWebDriverSteps;
import org.jbehave.web.selenium.SeleniumConfiguration;
import org.jbehave.web.selenium.SeleniumContext;
import org.jbehave.web.selenium.SeleniumStepMonitor;
import org.jbehave.web.selenium.TypeWebDriverProvider;
import org.jbehave.web.selenium.WebDriverProvider;
import org.jbehave.web.selenium.WebDriverScreenshotOnFailure;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

import static java.util.Arrays.asList;
import static org.jbehave.core.io.CodeLocations.codeLocationFromClass;
import static org.jbehave.core.reporters.StoryReporterBuilder.Format.HTML;
import static org.jbehave.core.reporters.StoryReporterBuilder.Format.IDE_CONSOLE;
import static org.jbehave.core.reporters.StoryReporterBuilder.Format.TXT;
import static org.jbehave.core.reporters.StoryReporterBuilder.Format.XML;

public class EtsyDotComStories extends JUnitStories {

    private WebDriverProvider driverProvider = new TypeWebDriverProvider();
    private Configuration configuration;
    private static ContextView contextView = new LocalFrameContextView().sized(640,48);

    static {
        System.setProperty("webdriver.firefox.profile", "WebDriver");
    }

    @Override
    public Configuration configuration() {
        configuration = makeConfiguration(this.getClass(), driverProvider);
        return configuration;
    }

    public static Configuration makeConfiguration(Class<?> embeddableClass, WebDriverProvider driverProvider) {

        return new SeleniumConfiguration()
            .useWebDriverProvider(driverProvider)
            .useSeleniumContext(new SeleniumContext())
            .useFailureStrategy(new FailingUponPendingStep())
            .useStepMonitor(new SeleniumStepMonitor(contextView, new SeleniumContext(), new SilentStepMonitor()))
            .useStoryLoader(new LoadFromClasspath(embeddableClass.getClassLoader()))
            .useStoryReporterBuilder(
                new MyStoryReporterBuilder(new SeleniumContext())
                    .withCodeLocation(CodeLocations.codeLocationFromClass(embeddableClass))
                    .withDefaultFormats()
                    .withFormats(IDE_CONSOLE, TXT, HTML, XML));
    }

    @Override
    public List<CandidateSteps> candidateSteps() {
        List<CandidateSteps> steps = makeGroovyCandidateSteps(configuration(), new GroovyResourceFinder(), driverProvider);
        steps.add(0, stepify(new PerStoriesWebDriverSteps(driverProvider))); // before other Groovy steps
        steps.add(stepify(new WebDriverScreenshotOnFailure(driverProvider)));

        return steps;
    }

    private Steps stepify(final Object steps) {
        return new Steps(configuration, steps);
    }

    public static List<CandidateSteps> makeGroovyCandidateSteps(final Configuration configuration, GroovyResourceFinder resourceFinder, final WebDriverProvider webDriverProvider) {

        GroovyContext context = new GroovyContext(resourceFinder) {
            @Override
            public Object newInstance(Class<?> parsedClass) {
                if (parsedClass.getName().contains("pages.")) {
                    return new Object();
                }
                try {
                    Object inst = null;
                    try {
                        inst = parsedClass.newInstance();
                        Method declaredMethod = parsedClass.getDeclaredMethod("setWebDriverProvider", WebDriverProvider.class);
                        declaredMethod.invoke(inst, webDriverProvider);
                    } catch (NoSuchMethodException e) {
                        // fine, it does not need a WebDriverProvider via setter.
                    }
                    return inst;
                } catch (IllegalAccessException e) {
                    System.out.println("--> iae");
                    return ""; // not a steps class, discard for the sake of steps registration
                } catch (InvocationTargetException e) {
                    System.out.println("--> ite");
                    return ""; // not a steps class, discard for the sake of steps registration
                } catch (InstantiationException e) {
                    System.out.println("--> ie " + parsedClass.getName());
                    return ""; // not a steps class, discard for the sake of steps registration
                }
            }
        };

        return new GroovyStepsFactory(configuration, context).createCandidateSteps();
    }

    @Override
    protected List<String> storyPaths() {
        return new StoryFinder()
                .findPaths(codeLocationFromClass(this.getClass()).getFile(), asList("**/*"+ storyFilter() +".story"), null);
    }


    /**
     * set this on the command line -DstepFilter=foo
     * @return
     */
    private String storyFilter() {
        String storyFilter = System.getProperty("storyFilter");
        if (storyFilter == null) {
            storyFilter = "";
        }
        return storyFilter;
    }

    private static class MyStoryReporterBuilder extends StoryReporterBuilder {
        private final SeleniumContext seleniumContext;

        public MyStoryReporterBuilder(SeleniumContext seleniumContext) {
            this.seleniumContext = seleniumContext;
        }

        @Override
        public StoryReporter reporterFor(String storyPath, Format format) {
            if ( format == IDE_CONSOLE ){
                return new ConsoleOutput(){
                    @Override
                    public void beforeScenario(String title, boolean givenStory) {
                        seleniumContext.setCurrentScenario(title);
                        super.beforeScenario(title, givenStory);
                    }

                    @Override
                    public void afterStory(boolean givenStory) {
                        contextView.close();
                        super.afterStory(givenStory);
                    }
                };
            } else {
                return super.reporterFor(storyPath, format);
            }
        }

    }
}
