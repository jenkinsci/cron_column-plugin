package hudson.plugins;

import hudson.Extension;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Job;
import hudson.scm.NullSCM;
import hudson.scm.SCM;
import hudson.triggers.SCMTrigger;
import hudson.triggers.TimerTrigger;
import hudson.triggers.Trigger;
import hudson.triggers.TriggerDescriptor;
import hudson.views.ListViewColumn;
import hudson.views.ListViewColumnDescriptor;
import io.jenkins.plugins.extended_timer_trigger.ExtendedTimerTrigger;
import java.util.Map;
import jenkins.model.Jenkins;
import jenkins.model.ParameterizedJobMixIn;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.parameterizedscheduler.ParameterizedTimerTrigger;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 * CronViewColumn
 *
 * Column plugin that adds a column to a jobs overview page.
 *
 * The column displays the cron expression of each Trigger on the Job.
 *
 * 03/03/2010
 * @author Eelco de Vlieger
 */
public class CronViewColumn extends ListViewColumn {

    private static final String CRON_EXPRESSION_COMMENT_START = "#";
    private static final String EXTENDED_CRON_EXPRESSION_COMMENT_START = "//";
    private static final String CRON_EXPRESSION_COMMENT_COLOR = "var(--dark-green)";
    private static final String CRON_EXPRESSION_PARAM_COLOR = "var(--blue)";

    private boolean hideComments;

    @DataBoundConstructor
    public CronViewColumn() {}

    public boolean getHideComments() {
        return hideComments;
    }

    @DataBoundSetter
    public void setHideComments(boolean hideComments) {
        this.hideComments = hideComments;
    }

    private boolean hasPlugin(String pluginName) {
        return Jenkins.get().getPlugin(pluginName) != null;
    }

    private String getCronSpec(Trigger<?> trigger) {
        if (hasPlugin("extended-timer-trigger") && trigger instanceof ExtendedTimerTrigger ett) {
            return ett.getCronSpec();
        }
        if (hasPlugin("parameterized-scheduler") && trigger instanceof ParameterizedTimerTrigger ptt) {
            return ptt.getParameterizedSpecification();
        } else {
            return trigger.getSpec();
        }
    }

    /**
     * @return HTML String containing the cron expression of each Trigger on the Job (when available).
     */
    public String getCronTrigger(Job<?, ?> job, boolean hideComments) {
        if (!(job instanceof ParameterizedJobMixIn.ParameterizedJob<?, ?> pj)) return "";

        StringBuilder expression = new StringBuilder();

        // Check if source code management is enabled.
        boolean hasSourceCodeManagement = false;
        if (job instanceof AbstractProject<?, ?> project) {
            SCM sourceCodeManagement = project.getScm();
            hasSourceCodeManagement = sourceCodeManagement != null && !(sourceCodeManagement instanceof NullSCM);
        }

        Map<TriggerDescriptor, Trigger<?>> triggers = pj.getTriggers();
        for (Trigger<?> trigger : triggers.values()) {
            if (trigger == null) continue;

            String cronExpression = getCronSpec(trigger);
            if (cronExpression == null || cronExpression.isBlank()) continue;

            cronExpression = formatComments(cronExpression, hideComments, trigger);

            // Cron expression can still be set when Source Code Management has been disabled.
            if (!hasSourceCodeManagement && trigger instanceof SCMTrigger) expression.append("<i>(Disabled) </i>");

            // Add trigger name and cron expression.
            expression
                    .append(getTriggerName(trigger))
                    .append(":<br/><div class='jenkins-!-margin-left-1'>")
                    .append(cronExpression)
                    .append("</div>");
        }

        return expression.toString();
    }

    /**
     * Change the font color on the comment text within a cron expression.
     */
    private String formatComments(String cronExpression, boolean hideComments, Trigger<?> trigger) {

        boolean isExtendedTimerTrigger = hasPlugin("extended-timer-trigger") && trigger instanceof ExtendedTimerTrigger;
        boolean isParameterizedTrigger =
                hasPlugin("parameterized-scheduler") && trigger instanceof ParameterizedTimerTrigger;

        StringBuilder formattedExpression = new StringBuilder();

        String[] expressionLines = cronExpression.split("\n");
        for (String expressionLine : expressionLines) {
            expressionLine = Util.escape(expressionLine).trim();
            if (expressionLine.isBlank()) {
                continue;
            }
            boolean hasComment = expressionLine.startsWith(CRON_EXPRESSION_COMMENT_START);
            boolean hasExtendedComment = isExtendedTimerTrigger
                    && !expressionLine.startsWith("%")
                    && expressionLine.contains(EXTENDED_CRON_EXPRESSION_COMMENT_START);
            int formatIndex = expressionLine.indexOf(EXTENDED_CRON_EXPRESSION_COMMENT_START);
            if (isExtendedTimerTrigger && expressionLine.startsWith("%")) {
                expressionLine = "<b style='color: " + CRON_EXPRESSION_PARAM_COLOR + ";'>" + expressionLine + "</b>";
            }
            if (!hasComment && !hasExtendedComment) {
                // No comment, so just add the original expression line.
                if (isParameterizedTrigger && expressionLine.contains("%")) {
                    // Highlight parameter references in parameterized cron expressions.
                    expressionLine = expressionLine.replaceAll(
                            "(%.*)", "<b style='color: " + CRON_EXPRESSION_PARAM_COLOR + ";'>$1</b>");
                }
                formattedExpression.append(expressionLine);
            } else {
                if (hideComments) {
                    if (hasComment || formatIndex == 0) {
                        // Entire line is a comment, so skip it.
                        continue;
                    }
                    // Extended comment found after expression, append only the expression part.
                    formattedExpression.append(expressionLine, 0, formatIndex);
                } else {
                    if (hasComment) {
                        // Classic comment found, the complete line is a comment.
                        formattedExpression.append("<b><em style='color: " + CRON_EXPRESSION_COMMENT_COLOR + ";'>");
                        formattedExpression.append(expressionLine);
                        formattedExpression.append("</em></b>");

                    } else {
                        formattedExpression.append(expressionLine, 0, formatIndex);
                        formattedExpression.append("<b><em style='color: " + CRON_EXPRESSION_COMMENT_COLOR + ";'>");
                        formattedExpression.append(expressionLine.substring(formatIndex));
                        formattedExpression.append("</em></b>");
                    }
                }
            }
            formattedExpression.append("<br/>");
        }

        return formattedExpression.toString().trim();
    }

    /**
     * Determines the trigger name.
     *
     * @return Name of the trigger.
     */
    private String getTriggerName(Trigger<?> trigger) {
        String type = trigger.getDescriptor().getDisplayName();
        if (type.isBlank()) {
            if (trigger instanceof SCMTrigger) type = "SCM polling";
            else if (trigger instanceof TimerTrigger) type = "Build Trigger";
            else type = "Unknown Type";
        }
        return type;
    }

    @Extension
    @Symbol("cronTrigger")
    public static final class DescriptorImpl extends ListViewColumnDescriptor {

        @Override
        public String getDisplayName() {
            return "Cron Trigger";
        }

        @Override
        public boolean shownByDefault() {
            return false;
        }
    }
}
