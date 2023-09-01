package cloud.eppo.android.logging;

import cloud.eppo.android.dto.SubjectAttributes;

public class Assignment {
    private String experiment;
    private String featureFlag;
    private String allocation;
    private String variation;
    private String subject;
    private String timestamp;
    private SubjectAttributes subjectAttributes;

    public Assignment(
            String experiment,
            String featureFlag,
            String allocation,
            String variation,
            String subject,
            String timestamp,
            SubjectAttributes subjectAttributes
    ) {
        this.experiment = experiment;
        this.featureFlag = featureFlag;
        this.allocation = allocation;
        this.variation = variation;
        this.subject = subject;
        this.timestamp = timestamp;
        this.subjectAttributes = subjectAttributes;
    }

    public String getExperiment() {
        return experiment;
    }

    public String getVariation() {
        return variation;
    }

    public String getSubject() {
        return subject;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public SubjectAttributes getSubjectAttributes() {
        return subjectAttributes;
    }

    @Override
    public String toString() {
        return "Subject " + subject + " assigned to variation " + variation + " in experiment " + experiment;
    }
}
