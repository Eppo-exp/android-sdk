package cloud.eppo.android.dto;

import java.util.Set;

public class Shard {

    private String salt;

    private Set<Range> ranges;

    public String getSalt() {
        return salt;
    }

    public void setSalt(String salt) {
        this.salt = salt;
    }

    public Set<Range> getRanges() {
        return ranges;
    }

    public void setRanges(Set<Range> ranges) {
        this.ranges = ranges;
    }
}
