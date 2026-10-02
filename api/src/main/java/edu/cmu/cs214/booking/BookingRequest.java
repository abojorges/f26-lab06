package edu.cmu.cs214.booking;

/**
 * Everything needed to create one booking, passed to
 * {@link BookingApi#createBooking(BookingRequest)}.
 *
 * <p>Start with {@link #of(String, long, long)} and add the optional parts:
 * <pre>{@code
 * BookingRequest.of("R1", 540, 600)
 *         .withWaitlistKey("party-of-four")
 *         .withNotes("needs a projector")
 * }</pre>
 *
 * <p>A request is an immutable value; each {@code with} method returns a new
 * one. It does no validation of its own. {@code createBooking} checks it and
 * decides what the fields mean.
 *
 * @param roomId      the room to book
 * @param startMinute first minute of the booking, inclusive
 * @param endMinute   first minute after the booking, exclusive
 * @param waitlistKey caller's waitlist key, or null to decline waitlisting
 * @param notes       free-text notes for the booking, or null for none
 */
public record BookingRequest(String roomId, long startMinute, long endMinute,
                             String waitlistKey, String notes) {

    /** A request for the range {@code [startMinute, endMinute)}, with no waitlist key and no notes. */
    public static BookingRequest of(String roomId, long startMinute, long endMinute) {
        return new BookingRequest(roomId, startMinute, endMinute, null, null);
    }

    /** This request with the given waitlist key, or null to decline waitlisting. */
    public BookingRequest withWaitlistKey(String waitlistKey) {
        return new BookingRequest(roomId, startMinute, endMinute, waitlistKey, notes);
    }

    /** This request with the given notes, or null for none. */
    public BookingRequest withNotes(String notes) {
        return new BookingRequest(roomId, startMinute, endMinute, waitlistKey, notes);
    }
}
