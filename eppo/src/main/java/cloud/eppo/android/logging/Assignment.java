package cloud.eppo.android.logging;

import cloud.eppo.android.dto.SubjectAttributes;
import cloud.eppo.android.util.Utils;

import java.util.Date;
import java.util.Map;

public class Assignment {
    private final String experiment;
    private final String featureFlag;
    private final String allocation;
    private final String variation;
    private final String subject;
    private final String timestamp;
    private final SubjectAttributes subjectAttributes;
    private final Map<String, String> extraLogging;
    private final Map<String, String> metaData;

    private Assignment(
            String experiment,
            String featureFlag,
            String allocation,
            String variation,
            String subject,
            String timestamp,
            SubjectAttributes subjectAttributes,
            Map<String, String> extraLogging,
            Map<String, String> metaData) {
        this.experiment = experiment;
        this.featureFlag = featureFlag;
        this.allocation = allocation;
        this.variation = variation;
        this.subject = subject;
        this.timestamp = timestamp;
        this.subjectAttributes = subjectAttributes;
        this.extraLogging = extraLogging;
        this.metaData = metaData;
    }

    public static Assignment createWithCurrentDate(
            String experiment,
            String featureFlag,
            String allocation,
            String variation,
            String subject,
            SubjectAttributes subjectAttributes,
            Map<String, String> extraLogging,
            Map<String, String> metaData
    ) {
        return new Assignment(experiment, featureFlag, allocation, variation, subject,
                Utils.getISODate(new Date()), subjectAttributes, extraLogging, metaData);
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
