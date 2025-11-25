/*
 * Copyright (c) Steven P. Goldsmith. All rights reserved.
 */
package com.codeferm.alarmbian;

import de.milchreis.uibooster.model.FormElement;
import de.milchreis.uibooster.model.FormElementChangeListener;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;

public class IconFormElement extends FormElement<JLabel> {

    private final ImageIcon imageIcon;
    private JLabel jLabel;

    /**
     * Constructs the element using a pre-processed ImageIcon.
     *
     * @param icon The resized and converted ImageIcon.
     */
    public IconFormElement(ImageIcon icon) {
        // Pass a default name or null to the superclass if required
        super(null);
        this.imageIcon = icon;
        // Throw an error early if the icon is null to prevent later crashes
        if (icon == null) {
            throw new IllegalArgumentException("ImageIcon cannot be null.");
        }
    }

    @Override
    public JComponent createComponent(final FormElementChangeListener onChange) {
        Box box = Box.createVerticalBox();
        // Use the stored ImageIcon, which is already ready
        jLabel = new JLabel(this.imageIcon);
        box.add(jLabel);
        return box;
    }

    @Override
    public void setEnabled(final boolean enable) {
        if (jLabel != null) {
            jLabel.setEnabled(enable);
        }
    }

    @Override
    public JLabel getValue() {
        return jLabel;
    }

    @Override
    public void setValue(final JLabel value) {
        if (jLabel != null && value != null) {
            jLabel.setIcon(value.getIcon());
        }
    }
}
