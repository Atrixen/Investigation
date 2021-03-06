/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package com.iespacomolla.investigacion.dao.mysql.jdbc;

import com.iespacomolla.investigacion.dao.DAOException;
import com.iespacomolla.investigacion.dao.EntidadDAO;
import com.iespacomolla.investigacion.modelo.Entidad;
import com.iespacomolla.investigacion.modelo.Proyecto;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;


public class mysqlEntidadJdbcDAO implements EntidadDAO{

    
    // Consultas básicas CRUD:
    final String INSERT = "INSERT INTO Entidad (nombre, ubicacion) VALUES (?, ?)";
    final String UPDATE = "UPDATE Entidad SET nombre=?, ubicacion=? WHERE entidad_id = ?";
    final String DELETE = "DELETE FROM Entidad WHERE entidad_id = ? ";
    final String GETONE = "SELECT * FROM Entidad WHERE entidad_id = ?";
    final String GETALL = "SELECT * FROM Entidad";  
    final String GETPROYECTOS = "SELECT p.proyecto_id\n" +
                                "FROM Entidad e\n" +
                            "LEFT OUTER JOIN entidad_proyecto e_p\n" +
                                "ON e.entidad_id = e_p.entidad_id\n" +
                            "LEFT OUTER JOIN Proyecto AS p\n" +
                                "ON e_p.proyecto_id = p.proyecto_id\n" +
                            "WHERE e_p.entidad_id = ?";
    
    //final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MMM-dd");  
    private Connection con;
    private ResultSet rs;
    private PreparedStatement pStat;
    
    public mysqlEntidadJdbcDAO(Connection con) {
        this.con = con;
    }
    
    
    @Override
    public void insertar(Entidad o) throws DAOException{
        
        pStat = null;
        try{
            pStat = con.prepareStatement(INSERT, Statement.RETURN_GENERATED_KEYS);
            pStat.setString(1, o.getNombre());
            pStat.setString(2, o.getUbicacion());
            
            if (pStat.executeUpdate() == 0)
                throw new DAOException("Puede que el objeto de inserción no haya sido persistido en la BD.");
            //Obtengo el id que me autogeneró el INSERT para el objeto:
            rs = pStat.getGeneratedKeys();
            if (rs.next())
                o.setEntidad_id(rs.getLong(1));
            else
                throw new DAOException("Fallo al asignar ID a este Campo.");
        }
        catch (SQLException ex){
            throw new DAOException("Error en SQL INSERT ", ex);
        }
        finally{
            
            cerrarBasura();
        }
    }

    @Override
    public void modificar(Entidad o) throws DAOException{
        pStat = null;
        try{
            pStat = con.prepareStatement(UPDATE);
            pStat.setString(1, o.getNombre());
            pStat.setString(2, o.getUbicacion());
            pStat.setLong(3, o.getEntidad_id());
            
            if (pStat.executeUpdate() == 0)
                throw new DAOException("Puede que no se haya modificado la fila en la BD.");
        }
        catch (SQLException ex){
            throw new DAOException("Error en SQL UPDATE Enitdad: ", ex);
        }
        finally{
            if (pStat != null){
                try{
                   pStat.close();
                }catch (SQLException ex){
                    throw new DAOException("Error en SQL INSERT", ex);
                }
            }
        }
    }

    @Override
    public void eliminar(Entidad o) throws DAOException{
        PreparedStatement pStat = null;
        try{
            pStat = con.prepareStatement(DELETE);
            pStat.setLong(1, o.getEntidad_id());
            
            if (pStat.executeUpdate() == 0)
                throw new DAOException("Puede que el objeto no haya sido eliminado de la BD.");
        }
        catch (SQLException ex){
            throw new DAOException("Error en SQL DELETE ", ex);
        }
        finally{
            if (pStat != null){
                try{
                   pStat.close();
                }catch (SQLException ex){
                    throw new DAOException("Error en SQL DELETE", ex);
                }
            }
        }
    }
    
    //Transforma los valores de ResultSet en un objeto:
    private Entidad conversion(int level) throws DAOException{
        
        ResultSet rs2 = null; //Aún puedo necesitar el ResultSet principal.
        try{
            long entidad_id = rs.getLong("entidad_id");
            String nombre = rs.getString("nombre");
            String ubicacion = rs.getString("ubicacion");
            
            List<Proyecto> proyectos = new ArrayList<>();
            pStat = null; //Ya no necesito el PreparedStatement. 
            
            try{
                mysqlProyectoJdbcDAO pDAO = new mysqlProyectoJdbcDAO(con);
                pStat = con.prepareStatement(GETPROYECTOS);
                pStat.setLong(1, entidad_id);
                rs2 = pStat.executeQuery();
                
                while (rs2.next()){
                    if (level < 1)
                        proyectos.add(pDAO.obtener(rs2.getLong("proyecto_id"),1));
                    else
                        proyectos.add(null);
                }
            }catch (SQLException ex){
                throw new DAOException("Error al obtener los proyectos financiados por esta entidad. ",ex);
            }
        
            Entidad e = new Entidad(nombre, ubicacion, proyectos);
            e.setEntidad_id(entidad_id);
            return e;
            
        }catch(SQLException ex){
            throw new DAOException("Error al obtener datos de la Entidad",ex);
        }
        finally{
            if (rs2 != null){
                try {
                    rs2.close();
                } catch (SQLException ex) {
                    throw new DAOException("Error al terminar de obtener datos de la Entidad", ex);
                }
            }
        }
    }
    
    @Override
    public Entidad obtener(Long id, int level) throws DAOException{
        pStat = null;
        Entidad c = null;
        rs = null;
        try{
            pStat = con.prepareStatement(GETONE);
            pStat.setLong(1, id);
            rs = pStat.executeQuery();
            
            if (rs.next())
                c = conversion(level);
            else{
                throw new DAOException("No se ha encontrado el registro de la tabla Campo");
            }     
        }
        catch (SQLException ex){
            throw new DAOException("Error en SQL GETONE ", ex);
        }
        finally{
            cerrarBasura();
        }
        return c;
    }

    @Override
    public List<Entidad> obtenerTodos() throws DAOException{   
        pStat = null;
        List<Entidad> entidades = new ArrayList();
        rs = null;
        try{
            pStat = con.prepareStatement(GETALL);
            rs = pStat.executeQuery();
            // obtenerTodos siempre tiene level 0
            while (rs.next())                
                entidades.add(conversion(0));  
            
        }
        catch (SQLException ex){
            throw new DAOException("Error en SQL GETALL ", ex);
        }
        finally{   
            cerrarBasura();
        }
        return entidades;
    }
    
    //Comprueba y cierra recursos
    private void cerrarBasura() throws DAOException{
        
        if (pStat != null){
                try{
                   pStat.close();
                }catch (SQLException ex){
                    throw new DAOException("Error al cerrar PreparedStatement. ", ex);
                }
            }
            if (rs != null){
                try{
                    rs.close();
                }catch(SQLException ex){
                    new DAOException("Error al cerrar ResultSet. ",ex);
                }
            }
    }

}
