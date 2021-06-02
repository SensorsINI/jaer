/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package net.sf.jaer.util;

import java.io.ObjectStreamException;
import java.io.Serializable;

import net.sf.jaer.Description;
import net.sf.jaer.DevelopmentStatus;

/**
 * Holds class name, Description, and DevelopmentStatus of the found subclasses
 */
public class ClassNameWithDescriptionAndDevelopmentStatus implements Serializable {

    private String className;
    private String description;
    private DevelopmentStatus developmentStatus;

    public ClassNameWithDescriptionAndDevelopmentStatus(String className, String description, DevelopmentStatus developmentStatus) {
        this.className = className;
        this.description = description;
        this.developmentStatus = developmentStatus;
    }

    public ClassNameWithDescriptionAndDevelopmentStatus(Class<?> c) {
        this.className = c.getName();
        if (c.isAnnotationPresent(Description.class)) {
            Description des = c.getAnnotation(Description.class);
            this.description = des.value();
        }
        if (c.isAnnotationPresent(DevelopmentStatus.class)) {
            DevelopmentStatus des = c.getAnnotation(DevelopmentStatus.class);
            developmentStatus = des;
        }
    }

    /**
     * Returns just the class name
     */
    @Override
	public String toString() {
        return getClassName();
    }

    /**
     * @return the className
     */
    public String getClassName() {
        return className;
    }

    /**
     * @param className the className to set
     */
    public void setClassName(String className) {
        this.className = className;
    }

    /**
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param description the description to set
     */
    public void setDescription(String description) {
        this.description = description;
    }

    /**
     * @return the developmentStatus
     */
    public DevelopmentStatus getDevelopmentStatus() {
        return developmentStatus;
    }

    /**
     * @param developmentStatus the developmentStatus to set
     */
    public void setDevelopmentStatus(DevelopmentStatus developmentStatus) {
        this.developmentStatus = developmentStatus;
    }

    private Object writeReplace() throws ObjectStreamException {
        return className;
    }

}
