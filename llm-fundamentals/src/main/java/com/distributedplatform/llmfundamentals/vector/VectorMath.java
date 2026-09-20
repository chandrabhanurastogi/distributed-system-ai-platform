package com.distributedplatform.llmfundamentals.vector;

import java.util.Objects;

public final class VectorMath {

    private VectorMath() {
        // Utility class - prevent instantiation
    }

    /*
    Dot Product Value  |   Angle Between Vectors |  Meaning in Plain English
    Positive (+)       | Less than 90 degrees    | The two vectors point in similar directions (they agree).
    Zero (0)           | Exact 90 degrees        | The two vectors are perpendicular (completely unrelated).
    Negative (-)       | Greater than 90 degrees | The two vectors point in opposite directions (they disagree).

    Here is a simpler, real-world explanation of why the Dot Product alone isn't enough, and why we need to divide by the magnitudes (Cosine Similarity):

---

### The Problem: Dot Product Mixes "Direction" and "Length" Together

The dot product does **two things at the same time**:
1. It measures how much two vectors point in the same direction (**meaning/topic**).
2. It multiplies by how long the vectors are (**word count/length**).

Because it multiplies by length, **longer vectors naturally get huge dot product scores**, even if they are less relevant!

---

### A Real-World Example: Search Query vs. Two Documents

Imagine you search for: **"refund policy"**

Now compare your search query to two documents:

#### Document 1: A Short FAQ (1 sentence)
- Text: *"Our refund policy gives 30 days for full return."*
- Direction: **100% identical meaning** to what you asked.
- Vector Length: Small (only 1 sentence).
- **Dot Product Result = 5**

#### Document 2: A 500-Page Financial Book
- Text: A huge textbook that mentions the word *"policy"* 200 times and *"refund"* 10 times in passing across 500 pages.
- Direction: **Only loosely related** to what you asked.
- Vector Length: Huge (hundreds of pages, so all coordinate numbers are large).
- **Dot Product Result = 250**

---

### Why is this a problem?

If you rank search results using **Dot Product alone**:
- The search engine looks at `250` vs `5`, and thinks Document 2 is **50 times better** than Document 1.
- But you, as a human, know Document 1 is the exact answer you wanted, and Document 2 only won because it is longer!

---

### The Solution: Cosine Similarity (Dividing Out the Length)

To fix this unfair advantage:
1. We calculate the length of your query: `magnitude(Query)`
2. We calculate the length of Document 1: `magnitude(Doc1)`
3. We divide the dot product by those lengths: `5 / (Query_Length * Doc1_Length)` → gives **1.0 (100% match)**

When we do the same for Document 2:
- Even though its dot product was `250`, its huge length divides it back down to **0.3 (30% match)**.

---

### In Plain Terms

- **Dot Product:** "Do they agree on direction, multiplied by how large they are?"
- **Magnitude:** "How large is each vector?"
- **Cosine Similarity:** "Do they agree on direction, **ignoring** how large they are?" (Always gives a clean score between `-1.0` and `+1.0`).
     */

    public static double dotProduct(double[] a, double[] b) {
        validatePair(a, b);

        double sum = 0.0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }

    public static double magnitude(double[] v) {
        Objects.requireNonNull(v, "Vector 'v' must not be null");
        return Math.sqrt(dotProduct(v, v));
    }

    public static double cosineSimilarity(double[] a, double[] b) {
        validatePair(a, b);

        double magA = magnitude(a);
        double magB = magnitude(b);

        if (magA == 0.0 || magB == 0.0) {
            throw new ZeroVectorException();
        }

        return dotProduct(a, b) / (magA * magB);
    }

    private static void validatePair(double[] a, double[] b) {
        Objects.requireNonNull(a, "Vector 'a' must not be null");
        Objects.requireNonNull(b, "Vector 'b' must not be null");

        if (a.length == 0 || b.length == 0) {
            throw new EmptyVectorException();
        }

        if (a.length != b.length) {
            throw new VectorDimensionMismatchException(a.length, b.length);
        }
    }
}
