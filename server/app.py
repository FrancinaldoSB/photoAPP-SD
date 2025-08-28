import http.server
import socketserver
import cgi
import json
import os
import datetime
import socket
import tkinter as tk
from tkinter import messagebox
from PIL import Image, ImageTk
import threading

# Função para obter o endereço IP da máquina
def get_ip_address():
    try:
        # Cria um socket temporário para descobrir qual interface é usada para conexões externas
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(("8.8.8.8", 80))  # Conecta com o DNS do Google
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return "127.0.0.1"  # Fallback para localhost

# Pasta para salvar as fotos
UPLOAD_FOLDER = 'uploads'
if not os.path.exists(UPLOAD_FOLDER):
    os.makedirs(UPLOAD_FOLDER)

# Porta do servidor
PORT = 5000

# Variáveis globais para a interface gráfica
root = None
label_status = None
photo_label = None
img_reference = None  # Referência global para a imagem

# Inicializa a interface Tkinter
def init_gui():
    global root, label_status, photo_label
    
    root = tk.Tk()
    root.title("Servidor de Fotos")
    root.geometry("600x500")
    
    label_status = tk.Label(root, text="Aguardando Foto!", font=("Arial", 20))
    label_status.pack(pady=20)
    
    photo_label = tk.Label(root)
    photo_label.pack(fill=tk.BOTH, expand=True, padx=20, pady=20)
    
    # Iniciar uma thread para atualizar a interface periodicamente
    def update_gui():
        while True:
            root.update()
            root.update_idletasks()
            threading.Event().wait(0.1)  # Pequeno delay para não consumir CPU

    gui_thread = threading.Thread(target=update_gui, daemon=True)
    gui_thread.start()

# Função para exibir imagem recebida
def show_image(image_path):
    global label_status, photo_label, img_reference
    
    try:
        img = Image.open(image_path)
        img.thumbnail((550, 400))  # Redimensiona para caber na tela
        
        # Guardar referência à imagem para evitar coleta de lixo
        img_reference = ImageTk.PhotoImage(img)
        
        # Atualizar interface em thread segura
        def update_ui():
            if label_status and photo_label:
                label_status.config(text="Foto Recebida!")
                photo_label.config(image=img_reference)
        
        # Usar método after para programar a atualização da UI na thread principal
        if root:
            root.after(100, update_ui)
            
    except Exception as e:
        print(f"ERRO ao exibir imagem: {str(e)}")

class ServerHandler(http.server.SimpleHTTPRequestHandler):
    
    def do_GET(self):
        # Resposta simples para qualquer requisição GET
        self.send_response(200)
        self.send_header('Content-type', 'text/plain')
        self.end_headers()
        self.wfile.write(b'Servidor ativo. Use POST para /upload para enviar fotos.')
    
    def do_POST(self):
        # Rota para upload de arquivos
        if self.path == '/upload':
            try:
                # Informações do cliente
                client_address = self.client_address[0]
                print(f"Recebendo upload do cliente: {client_address}")
                
                content_type, _ = cgi.parse_header(self.headers['Content-Type'])
                
                # Verifica se é um multipart form
                if content_type == 'multipart/form-data':
                    form = cgi.FieldStorage(
                        fp=self.rfile,
                        headers=self.headers,
                        environ={'REQUEST_METHOD': 'POST',
                                'CONTENT_TYPE': self.headers['Content-Type']}
                    )
                    
                    # Verifica se tem um campo 'file'
                    if 'file' in form:
                        fileitem = form['file']

                        if fileitem.file:
                            filename = "photo.jpg"
                            
                            # Salvar o arquivo
                            file_path = os.path.join(UPLOAD_FOLDER, filename)
                            with open(file_path, 'wb') as file:
                                file.write(fileitem.file.read())
                        
                            print(f"SUCESSO: Foto recebida de {client_address} e salva em: {file_path}")
                            
                            # Chamar a função para exibir a imagem após recebê-la
                            show_image(file_path)
                        
                        # Retornar sucesso
                        self.send_response(200)
                        self.send_header('Content-type', 'application/json')
                        self.end_headers()
                        response = json.dumps({'success': True, 'filename': filename})
                        self.wfile.write(response.encode())
                        return
                
                # Se chegou aqui, houve erro no upload
                print(f"ERRO: Upload falhou - arquivo não encontrado no formulário (cliente: {client_address})")
                self.send_response(400)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                response = json.dumps({'error': 'Arquivo não encontrado na requisição'})
                self.wfile.write(response.encode())
                
            except Exception as e:
                print(f"ERRO: Upload falhou - {str(e)}")
                self.send_response(500)
                self.send_header('Content-type', 'application/json')
                self.end_headers()
                response = json.dumps({'error': f'Erro interno: {str(e)}'})
                self.wfile.write(response.encode())
        else:
            # Se não for a rota de upload
            self.send_error(404, "Endpoint não encontrado")

if __name__ == '__main__':
    handler = ServerHandler
    
    # Permitir reutilizar a porta
    socketserver.TCPServer.allow_reuse_address = True
    
    # Obter IP local
    ip_address = get_ip_address()
    
    # Inicializar a interface gráfica antes de iniciar o servidor
    init_gui()
    
    # Iniciar o servidor em uma thread separada
    def run_server():
        with socketserver.TCPServer(("", PORT), handler) as httpd:
            print("\n" + "="*50)
            print(f"SERVIDOR DE FOTOS INICIADO")
            print("="*50)
            print(f"Endereço IP local: {ip_address}")
            print(f"Porta: {PORT}")
            print(f"URL completa: http://{ip_address}:{PORT}")
            print(f"Pasta de uploads: {os.path.abspath(UPLOAD_FOLDER)}")
            print("="*50)
            print(f"No aplicativo Android, use: {ip_address}:{PORT}")
            print(f"Pressione Ctrl+C para encerrar o servidor")
            print("="*50 + "\n")
            
            try:
                httpd.serve_forever()
            except KeyboardInterrupt:
                print("\nServidor encerrado pelo usuário.")
    
    # Iniciar servidor em thread separada
    server_thread = threading.Thread(target=run_server, daemon=True)
    server_thread.start()
    
    # Inicia o loop principal do Tkinter na thread principal
    try:
        root.mainloop()
    except KeyboardInterrupt:
        print("\nPrograma encerrado pelo usuário.")
