module com.tuvarna.bg.library {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.web;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.bootstrapfx.core;
    requires eu.hansolo.tilesfx;
    requires jdk.jfr;
    requires static lombok;
    requires java.desktop;
    requires java.sql;

    // Application entry
    exports com.tuvarna.bg.library;

    // Controllers
    exports com.tuvarna.bg.library.controllers;
    opens com.tuvarna.bg.library.controllers to javafx.fxml;

    // Entities (need both for TableView reflection & API visibility)
    exports com.tuvarna.bg.library.entity;
    opens com.tuvarna.bg.library.entity to javafx.base;
}
