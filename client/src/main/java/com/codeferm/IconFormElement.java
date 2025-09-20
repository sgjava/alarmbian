/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm;

import de.milchreis.uibooster.model.FormElement;
import de.milchreis.uibooster.model.FormElementChangeListener;
import java.awt.image.BufferedImage;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;

/**
 * Form element that doesn't resize like ImageFormElement.
 *
 * @author Steven P. Goldsmith
 * @version 1.0.0
 * @since 1.0.0
 */
class IconFormElement extends FormElement {

    JLabel jLabel;
    String fileName;

    public IconFormElement(String fileName) {
        super(null);
        this.fileName = fileName;
    }

    @Override
    public JComponent createComponent(final FormElementChangeListener onChange) {
        Box box = Box.createVerticalBox();
        jLabel = new JLabel(new ImageIcon(fileName));
        box.add(jLabel);
        return box;
    }

    @Override
    public void setEnabled(final boolean enable) {
        jLabel.setEnabled(enable);
    }

    @Override
    public Object getValue() {
        return jLabel;
    }

    @Override
    public void setValue(final Object value) {
        jLabel.setIcon(new ImageIcon((BufferedImage) value));
    }
}
