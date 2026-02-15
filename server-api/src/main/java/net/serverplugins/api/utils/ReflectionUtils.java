package net.serverplugins.api.utils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/** Utility class for reflection operations. */
public class ReflectionUtils {

    /**
     * Gets all fields from a class, including fields from superclasses.
     *
     * @param clazz The class to get fields from
     * @return Iterable of all fields (declared and inherited)
     */
    @NotNull
    public static Iterable<Field> getAllFields(@NotNull Class<?> clazz) {
        List<Field> fields = new ArrayList<>();

        // Add all declared fields from this class
        for (Field field : clazz.getDeclaredFields()) {
            fields.add(field);
        }

        // Recursively add fields from superclass
        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null) {
            for (Field field : getAllFields(superClass)) {
                fields.add(field);
            }
        }

        return fields;
    }

    /**
     * Gets a field value from an object, making it accessible if needed.
     *
     * @param object The object to get the field from
     * @param fieldName The name of the field
     * @return The field value, or null if not found
     */
    public static Object getFieldValue(@NotNull Object object, @NotNull String fieldName) {
        try {
            for (Field field : getAllFields(object.getClass())) {
                if (field.getName().equals(fieldName)) {
                    field.setAccessible(true);
                    return field.get(object);
                }
            }
        } catch (IllegalAccessException e) {
            // Field access failed
        }
        return null;
    }

    /**
     * Sets a field value on an object, making it accessible if needed.
     *
     * @param object The object to set the field on
     * @param fieldName The name of the field
     * @param value The value to set
     * @return true if successful, false otherwise
     */
    public static boolean setFieldValue(
            @NotNull Object object, @NotNull String fieldName, Object value) {
        try {
            for (Field field : getAllFields(object.getClass())) {
                if (field.getName().equals(fieldName)) {
                    field.setAccessible(true);
                    field.set(object, value);
                    return true;
                }
            }
        } catch (IllegalAccessException e) {
            // Field access failed
        }
        return false;
    }
}
