package fixture.vftv.api.alpha;

/** API_IS_PURE: the loader-free API depends on a library outside its allowed set. */
public interface Impure {
    /** A foreign type. */
    org.junit.jupiter.api.TestInfo info();
}
