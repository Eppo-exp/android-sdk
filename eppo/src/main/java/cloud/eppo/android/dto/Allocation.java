package cloud.eppo.android.dto;

import java.util.Date;
import java.util.List;
import java.util.Set;

public class Allocation {

    private String key;

    private List<TargetingRule> rules;

    private Date startAt;

    private Date endAt;

    private Set<Split> splits;

    private boolean doLog;

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public List<TargetingRule> getRules() {
        return rules;
    }

    public void setRules(List<TargetingRule> rules) {
        this.rules = rules;
    }

    public Date getStartAt() {
        return startAt;
    }

    public void setStartAt(Date startAt) {
        this.startAt = startAt;
    }

    public Date getEndAt() {
        return endAt;
    }

    public void setEndAt(Date endAt) {
        this.endAt = endAt;
    }

    public Set<Split> getSplits() {
        return splits;
    }

    public void setSplits(Set<Split> splits) {
        this.splits = splits;
    }

    public boolean doLog() {
        return doLog;
    }

    public void setDoLog(boolean doLog) {
        this.doLog = doLog;
    }
}
