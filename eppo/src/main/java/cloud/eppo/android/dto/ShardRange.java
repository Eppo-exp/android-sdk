package cloud.eppo.android.dto;

import com.google.gson.annotations.SerializedName;

public class ShardRange {
    @SerializedName("start")
    private int start;
    @SerializedName("end")
    private int end;

    public int getStart() {
        return start;
    }

    public void setStart(int start) {
        this.start = start;
    }

    public int getEnd() {
        return end;
    }

    public void setEnd(int end) {
        this.end = end;
    }
}
