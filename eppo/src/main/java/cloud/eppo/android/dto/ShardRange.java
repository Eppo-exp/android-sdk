package cloud.eppo.android.dto;

public class ShardRange {
    @SerializedName("start")
    private int start;
    @SerializedName("end")
    private int end;

    public int getStart() {
        return start;
    }

    public int getEnd() {
        return end;
    }

    public void setStart(int start) {
        this.start = start;
    }

    public void setEnd(int end) {
        this.end = end;
    }
}
