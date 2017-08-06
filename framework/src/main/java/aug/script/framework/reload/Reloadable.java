package aug.script.framework.reload;

public interface Reloadable {
    /**
     * <p>This id should be unique in the implementing class.</p>
     */
    Long getKey();
    void setKey(Long key);
}
