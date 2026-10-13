package com.github.drakescraft_labs.galactifun.base.items;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.github.drakescraft_labs.galactifun.base.items.StargateGeometry.Orientation;
import com.github.drakescraft_labs.galactifun.base.items.StargateGeometry.Pos;

/** Geometria del Stargate: forma, no solapamiento y orientaciones (sin servidor Bukkit). */
class StargateGeometryTest {

    @Test
    void elAnilloTiene15BloquesYElPortal21() {
        assertEquals(15, StargateGeometry.ANILLO.size());
        assertEquals(21, StargateGeometry.PORTAL.size());
    }

    @Test
    void anilloYPortalNoSeSolapanNiSeRepiten() {
        Set<Pos> anillo = new HashSet<>(StargateGeometry.ANILLO);
        Set<Pos> portal = new HashSet<>(StargateGeometry.PORTAL);
        assertEquals(15, anillo.size());
        assertEquals(21, portal.size());
        anillo.retainAll(portal);
        assertTrue(anillo.isEmpty(), "ningun bloque puede ser anillo y portal a la vez");
    }

    @Test
    void elAnilloEsSimetricoRespectoAlControlador() {
        for (Pos p : StargateGeometry.ANILLO) {
            assertTrue(StargateGeometry.ANILLO.contains(new Pos(p.y(), -p.h())), "falta el simetrico de " + p);
        }
    }

    @Test
    void elControladorNoEsPartedelAnilloNiDelPortal() {
        Pos controlador = new Pos(0, 0);
        assertFalse(StargateGeometry.ANILLO.contains(controlador));
        assertFalse(StargateGeometry.PORTAL.contains(controlador));
    }

    @Test
    void lasDosOrientacionesSonPlanosPerpendiculares() {
        assertArrayEquals(new int[] { 0, 2, 3 }, Orientation.EJE_Z.offset(2, 3));
        assertArrayEquals(new int[] { 3, 2, 0 }, Orientation.EJE_X.offset(2, 3));
        assertArrayEquals(new int[] { 1, 0, 0 }, Orientation.EJE_Z.normal());
        assertArrayEquals(new int[] { 0, 0, 1 }, Orientation.EJE_X.normal());
    }

    @Test
    void faltantesDevuelveSoloLoQueNoEsta() {
        List<Pos> faltan = StargateGeometry.faltantes(p -> !(p.y() == 6 && p.h() == 0));
        assertEquals(List.of(new Pos(6, 0)), faltan);
        assertTrue(StargateGeometry.faltantes(p -> true).isEmpty());
        assertEquals(15, StargateGeometry.faltantes(p -> false).size());
    }

    @Test
    void describirDaTextoEntendible() {
        assertTrue(StargateGeometry.describir(new Pos(0, 1)).contains("base"));
        assertTrue(StargateGeometry.describir(new Pos(6, 0)).contains("top"));
    }
}
