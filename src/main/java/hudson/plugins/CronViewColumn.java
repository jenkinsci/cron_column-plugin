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
import java.util.Map;
import jenkins.model.ParameterizedJobMixIn;
import org.jenkinsci.Symbol;
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
    private static final String CRON_EXPRESSION_COMMENT_COLOR = "var(--dark-green)";

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

            String cronExpression = trigger.getSpec();
            if (cronExpression == null || cronExpression.isBlank()) continue;

            cronExpression = formatComments(cronExpression, hideComments);

            // Cron expression can still be set when Source Code Management has been disabled.
            if (!hasSourceCodeManagement && trigger instanceof SCMTrigger) expression.append("<i>(Disabled) </i>");

            // Add trigger name and cron expression.
            expression.append(getTriggerName(trigger)).append(": ").append(cronExpression);
        }

        return expression.toString();
    }

    /**
     * Change the font color on the comment text within a cron expression.
     */
    private String formatComments(String cronExpression, boolean hideComments) {
        if (!cronExpression.contains(CRON_EXPRESSION_COMMENT_START)) return cronExpression; // No comment found.

        StringBuilder formattedExpression = new StringBuilder();

        String[] expressionLines = cronExpression.split("\n");
        for (String expressionLine : expressionLines) {
            expressionLine = Util.escape(expressionLine).trim();
            if (expressionLine.isBlank()) {
                continue;
            }
            int commentStartIndex = expressionLine.indexOf(CRON_EXPRESSION_COMMENT_START);
            if (commentStartIndex < 0) {
                // No comment, so just add the original expression line.
                formattedExpression.append(expressionLine);
            } else {
                if (hideComments) {
                    formattedExpression.append(expressionLine, 0, commentStartIndex);
                    continue;
                } else {
                    // Comment found, wrapping comment in font tags (setting the color).
                    formattedExpression.append(expressionLine, 0, commentStartIndex);
                    formattedExpression.append("<b><em style='color: " + CRON_EXPRESSION_COMMENT_COLOR + ";'>");
                    formattedExpression.append(expressionLine.substring(commentStartIndex));
                    formattedExpression.append("</em></b>");
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
