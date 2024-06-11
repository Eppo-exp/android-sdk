package cloud.eppo.android;

import cloud.eppo.ufc.dto.SubjectAttributes;
import cloud.eppo.ufc.dto.Variation;
import java.util.Map;

public class FlagEvaluationResult {

  private String flagKey;
  private String subjectKey;
  private SubjectAttributes subjectAttributes;
  private String allocationKey;
  private Variation variation;
  private Map<String, String> extraLogging;
  private boolean doLog;

  public String getFlagKey() {
    return flagKey;
  }

  public void setFlagKey(String flagKey) {
    this.flagKey = flagKey;
  }

  public String getSubjectKey() {
    return subjectKey;
  }

  public void setSubjectKey(String subjectKey) {
    this.subjectKey = subjectKey;
  }

  public SubjectAttributes getSubjectAttributes() {
    return subjectAttributes;
  }

  public void setSubjectAttributes(SubjectAttributes subjectAttributes) {
    this.subjectAttributes = subjectAttributes;
  }

  public String getAllocationKey() {
    return allocationKey;
  }

  public void setAllocationKey(String allocationKey) {
    this.allocationKey = allocationKey;
  }

  public Variation getVariation() {
    return variation;
  }

  public void setVariation(Variation variation) {
    this.variation = variation;
  }

  public Map<String, String> getExtraLogging() {
    return extraLogging;
  }

  public void setExtraLogging(Map<String, String> extraLogging) {
    this.extraLogging = extraLogging;
  }

  public boolean doLog() {
    return doLog;
  }

  public void setDoLog(boolean doLog) {
    this.doLog = doLog;
  }
}
